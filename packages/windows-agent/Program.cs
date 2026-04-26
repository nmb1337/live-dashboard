using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;

internal sealed class AgentConfig
{
    [JsonPropertyName("serverUrl")]
    public string ServerUrl { get; init; } = "http://127.0.0.1:3000";

    [JsonPropertyName("token")]
    public string Token { get; init; } = string.Empty;

    [JsonPropertyName("intervalSeconds")]
    public int IntervalSeconds { get; init; } = 10;

    [JsonPropertyName("userAgent")]
    public string UserAgent { get; init; } = "live-dashboard-windows-agent/1.0.0";
}

internal sealed class ReportPayload
{
    [JsonPropertyName("app_id")]
    public string AppId { get; init; } = string.Empty;

    [JsonPropertyName("window_title")]
    public string WindowTitle { get; init; } = string.Empty;

    [JsonPropertyName("timestamp")]
    public string Timestamp { get; init; } = string.Empty;
}

internal sealed class ForegroundSnapshot
{
    public string AppId { get; init; } = "windows.unknown";
    public string WindowTitle { get; init; } = string.Empty;
}

internal static partial class NativeMethods
{
    [DllImport("user32.dll")]
    public static extern nint GetForegroundWindow();

    [DllImport("user32.dll", SetLastError = true)]
    public static extern uint GetWindowThreadProcessId(nint hWnd, out uint processId);

    [DllImport("user32.dll", SetLastError = true, CharSet = CharSet.Unicode)]
    private static extern int GetWindowTextW(nint hWnd, StringBuilder lpString, int nMaxCount);

    public static string GetWindowTitle(nint hWnd)
    {
        var sb = new StringBuilder(512);
        _ = GetWindowTextW(hWnd, sb, sb.Capacity);
        return sb.ToString().Trim();
    }
}

static class Program
{
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNamingPolicy = null,
        WriteIndented = false,
    };

    public static async Task<int> Main(string[] args)
    {
        var configPath = args.Length > 0 ? args[0] : "appsettings.json";
        if (!File.Exists(configPath))
        {
            Console.Error.WriteLine($"[windows-agent] Config not found: {configPath}");
            Console.Error.WriteLine("[windows-agent] Copy appsettings.example.json to appsettings.json and fill token/serverUrl.");
            return 1;
        }

        AgentConfig? config;
        try
        {
            config = JsonSerializer.Deserialize<AgentConfig>(await File.ReadAllTextAsync(configPath), JsonOptions);
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine($"[windows-agent] Invalid config JSON: {ex.Message}");
            return 1;
        }

        if (config is null)
        {
            Console.Error.WriteLine("[windows-agent] Empty config.");
            return 1;
        }

        var baseUrl = NormalizeBaseUrl(config.ServerUrl);
        if (baseUrl is null)
        {
            Console.Error.WriteLine("[windows-agent] serverUrl must be a valid http:// or https:// address.");
            return 1;
        }

        if (string.IsNullOrWhiteSpace(config.Token))
        {
            Console.Error.WriteLine("[windows-agent] token is required.");
            return 1;
        }

        var interval = Math.Clamp(config.IntervalSeconds, 5, 120);
        using var client = new HttpClient
        {
            Timeout = TimeSpan.FromSeconds(15),
            BaseAddress = new Uri(baseUrl),
        };
        client.DefaultRequestHeaders.Authorization =
            new System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", config.Token.Trim());
        client.DefaultRequestHeaders.UserAgent.ParseAdd(config.UserAgent);

        Console.WriteLine($"[windows-agent] Reporting to {baseUrl}/api/report every {interval}s");
        Console.WriteLine("[windows-agent] Press Ctrl+C to stop.");

        using var cts = new CancellationTokenSource();
        Console.CancelKeyPress += (_, eventArgs) =>
        {
            eventArgs.Cancel = true;
            cts.Cancel();
        };

        while (!cts.Token.IsCancellationRequested)
        {
            try
            {
                var snapshot = ReadForegroundWindow();
                var payload = new ReportPayload
                {
                    AppId = snapshot.AppId,
                    WindowTitle = snapshot.WindowTitle,
                    Timestamp = DateTimeOffset.UtcNow.ToString("O"),
                };

                using var response = await client.PostAsync(
                    "/api/report",
                    new StringContent(JsonSerializer.Serialize(payload, JsonOptions), Encoding.UTF8, "application/json"),
                    cts.Token);

                if (!response.IsSuccessStatusCode)
                {
                    var body = await response.Content.ReadAsStringAsync(cts.Token);
                    Console.WriteLine($"[{DateTime.Now:HH:mm:ss}] HTTP {(int)response.StatusCode}: {body}");
                }
                else
                {
                    Console.WriteLine($"[{DateTime.Now:HH:mm:ss}] OK {snapshot.AppId} | {snapshot.WindowTitle}");
                }
            }
            catch (OperationCanceledException)
            {
                break;
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[{DateTime.Now:HH:mm:ss}] Error: {ex.Message}");
            }

            try
            {
                await Task.Delay(TimeSpan.FromSeconds(interval), cts.Token);
            }
            catch (OperationCanceledException)
            {
                break;
            }
        }

        Console.WriteLine("[windows-agent] Stopped.");
        return 0;
    }

    private static string? NormalizeBaseUrl(string value)
    {
        var trimmed = value.Trim().TrimEnd('/');
        if (string.IsNullOrWhiteSpace(trimmed)) return null;

        if (!Uri.TryCreate(trimmed, UriKind.Absolute, out var uri)) return null;
        var scheme = uri.Scheme.ToLowerInvariant();
        if (scheme != "http" && scheme != "https") return null;
        if (string.IsNullOrWhiteSpace(uri.Host)) return null;

        return trimmed;
    }

    private static ForegroundSnapshot ReadForegroundWindow()
    {
        var hwnd = NativeMethods.GetForegroundWindow();
        if (hwnd == nint.Zero)
        {
            return new ForegroundSnapshot
            {
                AppId = "windows.idle",
                WindowTitle = "",
            };
        }

        _ = NativeMethods.GetWindowThreadProcessId(hwnd, out var processId);
        var title = NativeMethods.GetWindowTitle(hwnd);

        string processName = "windows.unknown";
        if (processId != 0)
        {
            try
            {
                var process = Process.GetProcessById((int)processId);
                processName = process.ProcessName;
            }
            catch
            {
                processName = "windows.unknown";
            }
        }

        if (string.IsNullOrWhiteSpace(title))
        {
            title = processName;
        }

        return new ForegroundSnapshot
        {
            AppId = processName,
            WindowTitle = title,
        };
    }
}
