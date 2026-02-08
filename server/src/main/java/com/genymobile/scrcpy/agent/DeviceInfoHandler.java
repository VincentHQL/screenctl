package com.genymobile.scrcpy.agent;

import android.os.Build;
import android.os.Environment;
import android.os.StatFs;

import com.genymobile.scrcpy.device.DisplayInfo;
import com.genymobile.scrcpy.device.Size;
import com.genymobile.scrcpy.util.Ln;
import com.genymobile.scrcpy.wrappers.ServiceManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.DecimalFormat;

/**
 * Handler for device information requests
 * Returns comprehensive device information including hardware, OS, display, and storage
 * Example: GET /device-info
 */
public class DeviceInfoHandler implements HttpHandler {
    
    @Override
    public HttpResponse handle(HttpRequest request) {
        try {
            StringBuilder json = new StringBuilder();
            json.append("{");
            
            // Device basic info
            json.append("\"device\":{");
            json.append("\"manufacturer\":\"").append(escapeJson(Build.MANUFACTURER)).append("\",");
            json.append("\"brand\":\"").append(escapeJson(Build.BRAND)).append("\",");
            json.append("\"model\":\"").append(escapeJson(Build.MODEL)).append("\",");
            json.append("\"device\":\"").append(escapeJson(Build.DEVICE)).append("\",");
            json.append("\"product\":\"").append(escapeJson(Build.PRODUCT)).append("\",");
            json.append("\"hardware\":\"").append(escapeJson(Build.HARDWARE)).append("\",");
            json.append("\"board\":\"").append(escapeJson(Build.BOARD)).append("\",");
            json.append("\"serial\":\"").append(escapeJson(Build.SERIAL)).append("\"");
            json.append("},");
            
            // Android version info
            json.append("\"android\":{");
            json.append("\"version\":\"").append(Build.VERSION.RELEASE).append("\",");
            json.append("\"sdkInt\":").append(Build.VERSION.SDK_INT).append(",");
            json.append("\"codename\":\"").append(Build.VERSION.CODENAME).append("\",");
            json.append("\"incremental\":\"").append(escapeJson(Build.VERSION.INCREMENTAL)).append("\",");
            json.append("\"securityPatch\":\"").append(escapeJson(Build.VERSION.SECURITY_PATCH)).append("\"");
            json.append("},");
            
            // Build info
            json.append("\"build\":{");
            json.append("\"id\":\"").append(escapeJson(Build.ID)).append("\",");
            json.append("\"display\":\"").append(escapeJson(Build.DISPLAY)).append("\",");
            json.append("\"fingerprint\":\"").append(escapeJson(Build.FINGERPRINT)).append("\",");
            json.append("\"tags\":\"").append(escapeJson(Build.TAGS)).append("\",");
            json.append("\"type\":\"").append(escapeJson(Build.TYPE)).append("\",");
            json.append("\"time\":").append(Build.TIME);
            json.append("},");
            
            // Display info
            json.append("\"display\":");
            json.append(getDisplayInfo());
            json.append(",");
            
            // CPU info
            json.append("\"cpu\":");
            json.append(getCpuInfo());
            json.append(",");
            
            // Memory info
            json.append("\"memory\":");
            json.append(getMemoryInfo());
            json.append(",");
            
            // Storage info
            json.append("\"storage\":");
            json.append(getStorageInfo());
            json.append(",");
            
            // Battery info
            json.append("\"battery\":");
            json.append(getBatteryInfo());
            
            json.append("}");
            
            return HttpResponse.newFixedLengthResponse(
                HttpResponse.Status.OK,
                "application/json",
                json.toString()
            );
            
        } catch (Exception e) {
            Ln.e("Error getting device info", e);
            return HttpResponse.newFixedLengthResponse(
                HttpResponse.Status.INTERNAL_ERROR,
                "application/json",
                "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}"
            );
        }
    }
    
    /**
     * Get display information
     */
    private String getDisplayInfo() {
        StringBuilder json = new StringBuilder();
        json.append("{");
        
        try {
            DisplayInfo displayInfo = ServiceManager.getDisplayManager().getDisplayInfo(0);
            if (displayInfo != null) {
                Size size = displayInfo.getSize();
                json.append("\"width\":").append(size.getWidth()).append(",");
                json.append("\"height\":").append(size.getHeight()).append(",");
                json.append("\"rotation\":").append(displayInfo.getRotation()).append(",");
                json.append("\"layerStack\":").append(displayInfo.getLayerStack()).append(",");
                json.append("\"flags\":").append(displayInfo.getFlags());
            } else {
                json.append("\"error\":\"Display info not available\"");
            }
        } catch (Exception e) {
            json.append("\"error\":\"").append(escapeJson(e.getMessage())).append("\"");
        }
        
        json.append("}");
        return json.toString();
    }
    
    /**
     * Get CPU information
     */
    private String getCpuInfo() {
        StringBuilder json = new StringBuilder();
        json.append("{");
        
        // CPU architecture
        json.append("\"abi\":\"").append(escapeJson(Build.CPU_ABI)).append("\",");
        json.append("\"abi2\":\"").append(escapeJson(Build.CPU_ABI2)).append("\",");
        
        // Supported ABIs
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            json.append("\"supportedAbis\":[");
            String[] abis = Build.SUPPORTED_ABIS;
            for (int i = 0; i < abis.length; i++) {
                if (i > 0) json.append(",");
                json.append("\"").append(escapeJson(abis[i])).append("\"");
            }
            json.append("],");
        }
        
        // CPU cores
        int cores = Runtime.getRuntime().availableProcessors();
        json.append("\"cores\":").append(cores).append(",");
        
        // CPU info from /proc/cpuinfo
        String cpuModel = getCpuModel();
        if (cpuModel != null) {
            json.append("\"model\":\"").append(escapeJson(cpuModel)).append("\",");
        }
        
        // CPU frequency
        String[] frequencies = getCpuFrequencies();
        if (frequencies != null) {
            json.append("\"frequencies\":{");
            json.append("\"min\":\"").append(escapeJson(frequencies[0])).append("\",");
            json.append("\"max\":\"").append(escapeJson(frequencies[1])).append("\",");
            json.append("\"current\":\"").append(escapeJson(frequencies[2])).append("\"");
            json.append("}");
        } else {
            json.append("\"frequencies\":null");
        }
        
        json.append("}");
        return json.toString();
    }
    
    /**
     * Get CPU model from /proc/cpuinfo
     */
    private String getCpuModel() {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader("/proc/cpuinfo"));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("Hardware") || line.startsWith("Processor")) {
                    String[] parts = line.split(":");
                    if (parts.length > 1) {
                        return parts[1].trim();
                    }
                }
            }
        } catch (IOException e) {
            // Ignore
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
        return null;
    }
    
    /**
     * Get CPU frequencies (min, max, current)
     */
    private String[] getCpuFrequencies() {
        String[] result = new String[3];
        try {
            result[0] = readFile("/sys/devices/system/cpu/cpu0/cpufreq/scaling_min_freq");
            result[1] = readFile("/sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq");
            result[2] = readFile("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq");
            
            // Convert from kHz to MHz
            for (int i = 0; i < 3; i++) {
                if (result[i] != null) {
                    try {
                        long khz = Long.parseLong(result[i].trim());
                        result[i] = (khz / 1000) + " MHz";
                    } catch (NumberFormatException e) {
                        // Keep original value
                    }
                }
            }
            
            return result;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Get memory information
     */
    private String getMemoryInfo() {
        StringBuilder json = new StringBuilder();
        json.append("{");
        
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader("/proc/meminfo"));
            String line;
            long totalMem = 0;
            long availMem = 0;
            
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("MemTotal:")) {
                    totalMem = parseMeminfoLine(line);
                } else if (line.startsWith("MemAvailable:")) {
                    availMem = parseMeminfoLine(line);
                }
            }
            
            json.append("\"total\":\"").append(formatBytes(totalMem * 1024)).append("\",");
            json.append("\"totalBytes\":").append(totalMem * 1024).append(",");
            json.append("\"available\":\"").append(formatBytes(availMem * 1024)).append("\",");
            json.append("\"availableBytes\":").append(availMem * 1024).append(",");
            json.append("\"used\":\"").append(formatBytes((totalMem - availMem) * 1024)).append("\",");
            json.append("\"usedBytes\":").append((totalMem - availMem) * 1024).append(",");
            
            if (totalMem > 0) {
                double usagePercent = ((double)(totalMem - availMem) / totalMem) * 100;
                json.append("\"usagePercent\":").append(String.format("%.2f", usagePercent));
            } else {
                json.append("\"usagePercent\":0");
            }
            
        } catch (Exception e) {
            json.append("\"error\":\"").append(escapeJson(e.getMessage())).append("\"");
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
        
        json.append("}");
        return json.toString();
    }
    
    /**
     * Parse memory info line (format: "MemTotal:       3951864 kB")
     */
    private long parseMeminfoLine(String line) {
        String[] parts = line.split("\\s+");
        if (parts.length >= 2) {
            try {
                return Long.parseLong(parts[1]);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }
    
    /**
     * Get storage information
     */
    private String getStorageInfo() {
        StringBuilder json = new StringBuilder();
        json.append("{");
        
        try {
            // Internal storage
            File dataDir = Environment.getDataDirectory();
            StatFs dataStat = new StatFs(dataDir.getPath());
            long dataTotal = dataStat.getTotalBytes();
            long dataAvailable = dataStat.getAvailableBytes();
            long dataUsed = dataTotal - dataAvailable;
            
            json.append("\"internal\":{");
            json.append("\"total\":\"").append(formatBytes(dataTotal)).append("\",");
            json.append("\"totalBytes\":").append(dataTotal).append(",");
            json.append("\"available\":\"").append(formatBytes(dataAvailable)).append("\",");
            json.append("\"availableBytes\":").append(dataAvailable).append(",");
            json.append("\"used\":\"").append(formatBytes(dataUsed)).append("\",");
            json.append("\"usedBytes\":").append(dataUsed).append(",");
            json.append("\"usagePercent\":").append(String.format("%.2f", ((double)dataUsed / dataTotal) * 100));
            json.append("},");
            
            // External storage (if available)
            String externalState = Environment.getExternalStorageState();
            json.append("\"external\":{");
            json.append("\"state\":\"").append(escapeJson(externalState)).append("\"");
            
            if (Environment.MEDIA_MOUNTED.equals(externalState)) {
                File externalDir = Environment.getExternalStorageDirectory();
                StatFs externalStat = new StatFs(externalDir.getPath());
                long externalTotal = externalStat.getTotalBytes();
                long externalAvailable = externalStat.getAvailableBytes();
                long externalUsed = externalTotal - externalAvailable;
                
                json.append(",\"total\":\"").append(formatBytes(externalTotal)).append("\",");
                json.append("\"totalBytes\":").append(externalTotal).append(",");
                json.append("\"available\":\"").append(formatBytes(externalAvailable)).append("\",");
                json.append("\"availableBytes\":").append(externalAvailable).append(",");
                json.append("\"used\":\"").append(formatBytes(externalUsed)).append("\",");
                json.append("\"usedBytes\":").append(externalUsed).append(",");
                json.append("\"usagePercent\":").append(String.format("%.2f", ((double)externalUsed / externalTotal) * 100));
            }
            json.append("}");
            
        } catch (Exception e) {
            json.append("\"error\":\"").append(escapeJson(e.getMessage())).append("\"");
        }
        
        json.append("}");
        return json.toString();
    }
    
    /**
     * Get battery information
     */
    private String getBatteryInfo() {
        StringBuilder json = new StringBuilder();
        json.append("{");
        
        try {
            // Try to read battery info from sysfs
            String level = readFile("/sys/class/power_supply/battery/capacity");
            String status = readFile("/sys/class/power_supply/battery/status");
            String health = readFile("/sys/class/power_supply/battery/health");
            String temperature = readFile("/sys/class/power_supply/battery/temp");
            String voltage = readFile("/sys/class/power_supply/battery/voltage_now");
            
            if (level != null) {
                json.append("\"level\":").append(level.trim()).append(",");
            }
            if (status != null) {
                json.append("\"status\":\"").append(escapeJson(status.trim())).append("\",");
            }
            if (health != null) {
                json.append("\"health\":\"").append(escapeJson(health.trim())).append("\",");
            }
            if (temperature != null) {
                try {
                    float temp = Float.parseFloat(temperature.trim()) / 10.0f;
                    json.append("\"temperature\":").append(String.format("%.1f", temp)).append(",");
                    json.append("\"temperatureUnit\":\"°C\",");
                } catch (NumberFormatException e) {
                    // Ignore
                }
            }
            if (voltage != null) {
                try {
                    float volt = Float.parseFloat(voltage.trim()) / 1000000.0f;
                    json.append("\"voltage\":").append(String.format("%.2f", volt)).append(",");
                    json.append("\"voltageUnit\":\"V\"");
                } catch (NumberFormatException e) {
                    json.append("\"voltage\":null");
                }
            } else {
                json.append("\"voltage\":null");
            }
            
        } catch (Exception e) {
            json.append("\"error\":\"").append(escapeJson(e.getMessage())).append("\"");
        }
        
        json.append("}");
        return json.toString();
    }
    
    /**
     * Read file content
     */
    private String readFile(String path) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(path));
            String line = reader.readLine();
            return line;
        } catch (Exception e) {
            return null;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
    }
    
    /**
     * Format bytes to human readable format
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        DecimalFormat df = new DecimalFormat("#.##");
        return df.format(bytes / Math.pow(1024, exp)) + " " + pre + "B";
    }
    
    /**
     * Escape string for JSON
     */
    private String escapeJson(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}
