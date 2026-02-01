package com.genymobile.scrcpy.monitor;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.genymobile.scrcpy.util.Ln;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Handler for listing installed packages
 * Supports pagination with ?page=X&pageSize=Y parameters
 * Example: GET /packages?page=0&pageSize=20
 */
public class PackageListHandler implements HttpHandler {
    
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 500;
    
    private final PackageManager packageManager;
    
    public PackageListHandler(PackageManager packageManager) {
        this.packageManager = packageManager;
    }
    
    @Override
    public HttpResponse handle(HttpRequest request) {
        try {
            // Parse pagination parameters
            int page = parseIntParam(request, "page", 0);
            int pageSize = parseIntParam(request, "pageSize", DEFAULT_PAGE_SIZE);
            
            // Validate parameters
            if (page < 0) {
                return HttpResponse.newFixedLengthResponse(
                    HttpResponse.Status.BAD_REQUEST,
                    "application/json",
                    "{\"error\":\"Page must be >= 0\"}"
                );
            }
            
            if (pageSize <= 0 || pageSize > MAX_PAGE_SIZE) {
                return HttpResponse.newFixedLengthResponse(
                    HttpResponse.Status.BAD_REQUEST,
                    "application/json",
                    "{\"error\":\"Page size must be between 1 and " + MAX_PAGE_SIZE + "\"}"
                );
            }
            
            // Parse filter parameters
            boolean systemApps = parseBoolParam(request, "system", true);
            boolean userApps = parseBoolParam(request, "user", true);
            String nameFilter = request.getParam("name");
            
            // Get all installed packages
            List<PackageInfo> allPackages = packageManager.getInstalledPackages(
                PackageManager.GET_META_DATA | PackageManager.GET_PERMISSIONS
            );
            
            // Filter packages
            List<PackageInfo> filteredPackages = filterPackages(
                allPackages, 
                systemApps, 
                userApps, 
                nameFilter
            );
            
            // Sort by package name
            Collections.sort(filteredPackages, new Comparator<PackageInfo>() {
                @Override
                public int compare(PackageInfo p1, PackageInfo p2) {
                    return p1.packageName.compareTo(p2.packageName);
                }
            });
            
            // Calculate pagination
            int totalCount = filteredPackages.size();
            int totalPages = (int) Math.ceil((double) totalCount / pageSize);
            int startIndex = page * pageSize;
            int endIndex = Math.min(startIndex + pageSize, totalCount);
            
            // Check if page is out of range
            if (startIndex >= totalCount && totalCount > 0) {
                return HttpResponse.newFixedLengthResponse(
                    HttpResponse.Status.BAD_REQUEST,
                    "application/json",
                    "{\"error\":\"Page " + page + " out of range. Total pages: " + totalPages + "\"}"
                );
            }
            
            // Get packages for current page
            List<PackageInfo> pagePackages = filteredPackages.subList(
                startIndex, 
                endIndex
            );
            
            // Build JSON response manually
            StringBuilder json = new StringBuilder();
            json.append("{");
            json.append("\"page\":").append(page).append(",");
            json.append("\"pageSize\":").append(pageSize).append(",");
            json.append("\"totalCount\":").append(totalCount).append(",");
            json.append("\"totalPages\":").append(totalPages).append(",");
            json.append("\"hasNext\":").append(endIndex < totalCount).append(",");
            json.append("\"hasPrevious\":").append(page > 0).append(",");
            json.append("\"packages\":[");
            
            for (int i = 0; i < pagePackages.size(); i++) {
                if (i > 0) {
                    json.append(",");
                }
                json.append(buildPackageJson(pagePackages.get(i)));
            }
            
            json.append("]}");
            
            return HttpResponse.newFixedLengthResponse(
                HttpResponse.Status.OK,
                "application/json",
                json.toString()
            );
            
        } catch (Exception e) {
            Ln.e("Error listing packages", e);
            return HttpResponse.newFixedLengthResponse(
                HttpResponse.Status.INTERNAL_ERROR,
                "application/json",
                "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}"
            );
        }
    }
    
    /**
     * Filter packages based on criteria
     */
    private List<PackageInfo> filterPackages(
        List<PackageInfo> packages,
        boolean includeSystem,
        boolean includeUser,
        String nameFilter
    ) {
        List<PackageInfo> filtered = new ArrayList<>();
        
        for (PackageInfo pkg : packages) {
            ApplicationInfo appInfo = pkg.applicationInfo;
            if (appInfo == null) {
                continue;
            }
            
            // Check if system app
            boolean isSystemApp = (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            
            // Filter by app type
            if (isSystemApp && !includeSystem) {
                continue;
            }
            if (!isSystemApp && !includeUser) {
                continue;
            }
            
            // Filter by name
            if (nameFilter != null && !nameFilter.isEmpty()) {
                String packageName = pkg.packageName.toLowerCase();
                String appLabel = "";
                try {
                    appLabel = appInfo.loadLabel(packageManager).toString().toLowerCase();
                } catch (Exception e) {
                    // Ignore
                }
                
                String filter = nameFilter.toLowerCase();
                if (!packageName.contains(filter) && !appLabel.contains(filter)) {
                    continue;
                }
            }
            
            filtered.add(pkg);
        }
        
        return filtered;
    }
    
    /**
     * Build JSON object for a package
     */
    private String buildPackageJson(PackageInfo pkg) {
        StringBuilder json = new StringBuilder();
        ApplicationInfo appInfo = pkg.applicationInfo;
        
        json.append("{");
        json.append("\"packageName\":\"").append(escapeJson(pkg.packageName)).append("\",");
        json.append("\"versionName\":\"").append(escapeJson(pkg.versionName != null ? pkg.versionName : "")).append("\",");
        json.append("\"versionCode\":").append(pkg.versionCode).append(",");
        
        if (appInfo != null) {
            // App label
            String label;
            try {
                label = appInfo.loadLabel(packageManager).toString();
            } catch (Exception e) {
                label = pkg.packageName;
            }
            json.append("\"label\":\"").append(escapeJson(label)).append("\",");
            
            // System app flag
            boolean isSystemApp = (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            json.append("\"isSystemApp\":").append(isSystemApp).append(",");
            
            // Enabled state
            json.append("\"enabled\":").append(appInfo.enabled).append(",");
            
            // Install location
            json.append("\"sourceDir\":\"").append(escapeJson(appInfo.sourceDir)).append("\",");
            json.append("\"dataDir\":\"").append(escapeJson(appInfo.dataDir)).append("\",");
            
            // UID
            json.append("\"uid\":").append(appInfo.uid).append(",");
            
            // Target SDK
            json.append("\"targetSdkVersion\":").append(appInfo.targetSdkVersion).append(",");
            
            // Min SDK (if available)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                json.append("\"minSdkVersion\":").append(appInfo.minSdkVersion).append(",");
            }
        }
        
        // First install time
        json.append("\"firstInstallTime\":").append(pkg.firstInstallTime).append(",");
        json.append("\"lastUpdateTime\":").append(pkg.lastUpdateTime).append(",");
        
        // Permissions count
        int permCount = (pkg.requestedPermissions != null) ? pkg.requestedPermissions.length : 0;
        json.append("\"permissionsCount\":").append(permCount);
        
        json.append("}");
        
        return json.toString();
    }
    
    /**
     * Parse integer parameter from request
     */
    private int parseIntParam(HttpRequest request, String paramName, int defaultValue) {
        String value = request.getParam(paramName);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    /**
     * Parse boolean parameter from request
     */
    private boolean parseBoolParam(HttpRequest request, String paramName, boolean defaultValue) {
        String value = request.getParam(paramName);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }
    
    /**
     * Escape string for JSON
     */
    private String escapeJson(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("\"", "\\\"")
                  .replace("\\", "\\\\")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}
