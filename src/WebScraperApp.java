import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.datatransfer.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.regex.*;
import org.jsoup.*;
import org.jsoup.nodes.*;
import org.jsoup.select.*;
import javax.net.ssl.SSLHandshakeException;

// ============================================================================
//                          CUSTOM DSA: QUEUE
// ============================================================================

/**
 * Custom Generic Circular Queue implementation using array.
 * Features: O(1) enqueue/dequeue, no memory waste.
 */
class QueueDSA<T> {
    private Object[] elements;
    private int front;
    private int rear;
    private int size;
    private static final int DEFAULT_CAPACITY = 16;

    public QueueDSA() {
        this.elements = new Object[DEFAULT_CAPACITY];
        this.front = 0;
        this.rear = -1;
        this.size = 0;
    }

    public void enqueue(T element) {
        if (size == elements.length) {
            resize();
        }
        rear = (rear + 1) % elements.length;
        elements[rear] = element;
        size++;
    }

    @SuppressWarnings("unchecked")
    public T dequeue() {
        if (isEmpty()) {
            throw new IllegalStateException("Queue is empty");
        }
        Object element = elements[front];
        elements[front] = null;
        front = (front + 1) % elements.length;
        size--;
        return (T) element;
    }

    @SuppressWarnings("unchecked")
    public T peek() {
        if (isEmpty()) {
            throw new IllegalStateException("Queue is empty");
        }
        return (T) elements[front];
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public int size() {
        return size;
    }

    private void resize() {
        Object[] newElements = new Object[elements.length * 2];
        for (int i = 0; i < size; i++) {
            newElements[i] = elements[(front + i) % elements.length];
        }
        elements = newElements;
        front = 0;
        rear = size - 1;
    }
}

// ============================================================================
// CUSTOM DSA: HTML STORAGE
// ============================================================================

/**
 * Efficient HTML content storage using StringBuilder.
 * Features: Fast append operations, memory efficient.
 */
class HtmlStorage {
    private StringBuilder content;

    public HtmlStorage() {
        this.content = new StringBuilder();
    }

    public void append(String text) {
        if (text != null) {
            content.append(text);
        }
    }

    public void appendLine(String text) {
        append(text);
        content.append("\n");
    }

    public String get() {
        return content.toString();
    }

    public void clear() {
        content = new StringBuilder();
    }

    public int length() {
        return content.length();
    }

    public boolean isEmpty() {
        return content.length() == 0;
    }
}

// ============================================================================
// URL VALIDATOR
// ============================================================================

/**
 * Validates URL format and structure using regex patterns.
 */
class UrlValidator {
    private static final String URL_REGEX = "^(https?|ftp)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]$";
    private static final Pattern URL_PATTERN = Pattern.compile(URL_REGEX);

    public static boolean isValid(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        return URL_PATTERN.matcher(url.trim()).matches();
    }

    public static String sanitize(String url) {
        if (url == null) {
            return "";
        }
        String trimmed = url.trim();
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://") &&
                !trimmed.startsWith("ftp://")) {
            trimmed = "https://" + trimmed;
        }
        return trimmed;
    }
}

// ============================================================================
// WEBSITE DOWNLOADER (NO SELENIUM)
// ============================================================================

/**
 * Full website downloader with complete folder structure
 * Creates local copy with all assets and proper relative paths
 * Uses only Jsoup and Java URL connection - no Selenium
 */
class WebsiteDownloader {
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
    private static final int TIMEOUT_MS = 30000;
    private static final Set<String> downloadedUrls = new HashSet<>();
    private static final Map<String, String> urlToLocalPath = new HashMap<>();

    public static class DownloadResult {
        public boolean success = false;
        public String message = "";
        public File projectFolder = null;
        public int totalFiles = 0;
        public long totalSize = 0;
        public long downloadTime = 0;
    }

    public static DownloadResult downloadFullWebsite(String urlString, File outputDir) {
        DownloadResult result = new DownloadResult();
        long startTime = System.currentTimeMillis();
        downloadedUrls.clear();
        urlToLocalPath.clear();

        try {
            // Create project folder
            URL url = new URL(urlString);
            String domain = url.getHost();
            String folderName = domain.replace(".", "_") + "_website_" +
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            File projectFolder = new File(outputDir, folderName);

            // Create folder structure
            File cssFolder = new File(projectFolder, "css");
            File jsFolder = new File(projectFolder, "js");
            File imagesFolder = new File(projectFolder, "images");
            File fontsFolder = new File(projectFolder, "fonts");
            File mediaFolder = new File(projectFolder, "media");
            File otherFolder = new File(projectFolder, "other");

            // Create directories
            if (!projectFolder.mkdirs() && !projectFolder.exists()) {
                throw new IOException("Failed to create project folder: " + projectFolder.getAbsolutePath());
            }
            cssFolder.mkdirs();
            jsFolder.mkdirs();
            imagesFolder.mkdirs();
            fontsFolder.mkdirs();
            mediaFolder.mkdirs();
            otherFolder.mkdirs();

            // Get HTML using Jsoup (no JavaScript rendering)
            Connection.Response response = Jsoup.connect(urlString)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .followRedirects(true)
                    .ignoreHttpErrors(true)
                    .execute();

            Document doc = response.parse();
            String baseUrl = response.url().toString();

            // Download all assets first
            downloadAllAssets(doc, baseUrl, projectFolder, result);

            // Process and save main HTML with local paths
            String processedHtml = processHtmlForLocal(doc, baseUrl, projectFolder);
            Files.write(new File(projectFolder, "index.html").toPath(),
                    processedHtml.getBytes(StandardCharsets.UTF_8));
            result.totalFiles++;
            result.totalSize += processedHtml.length();

            // Create comprehensive source code text file
            createSourceCodeFile(projectFolder, doc, urlString, result.totalFiles);

            // Create structure_prompt.txt
            createStructurePrompt(projectFolder, domain, urlString, result.totalFiles);

            // Create README.md
            createReadme(projectFolder, domain, urlString);

            result.success = true;
            result.message = String.format("Successfully downloaded %d files (%.2f KB) to: %s",
                    result.totalFiles, result.totalSize / 1024.0, projectFolder.getAbsolutePath());
            result.projectFolder = projectFolder;

        } catch (Exception e) {
            result.success = false;
            result.message = "Download failed: " + e.getMessage();
            e.printStackTrace();
        } finally {
            downloadedUrls.clear();
            urlToLocalPath.clear();
        }

        result.downloadTime = System.currentTimeMillis() - startTime;
        return result;
    }

    private static void downloadAllAssets(Document doc, String baseUrl, File projectFolder, DownloadResult result) {
        try {
            // Download CSS files
            Elements cssLinks = doc.select("link[rel=stylesheet]");
            for (Element css : cssLinks) {
                String href = css.attr("abs:href");
                if (!href.isEmpty() && !downloadedUrls.contains(href) && isDownloadableResource(href)) {
                    downloadResource(href, "css", projectFolder, result);
                }
            }

            // Download JS files
            Elements jsScripts = doc.select("script[src]");
            for (Element script : jsScripts) {
                String src = script.attr("abs:src");
                if (!src.isEmpty() && !downloadedUrls.contains(src) && isDownloadableResource(src)) {
                    downloadResource(src, "js", projectFolder, result);
                }
            }

            // Download images
            Elements images = doc.select("img[src]");
            for (Element img : images) {
                String src = img.attr("abs:src");
                if (!src.isEmpty() && !downloadedUrls.contains(src) && isDownloadableResource(src)) {
                    downloadResource(src, "images", projectFolder, result);
                }
            }

            // Download favicons and icons
            Elements icons = doc.select("link[rel~=icon], link[rel~=apple-touch-icon]");
            for (Element icon : icons) {
                String href = icon.attr("abs:href");
                if (!href.isEmpty() && !downloadedUrls.contains(href) && isDownloadableResource(href)) {
                    downloadResource(href, "images", projectFolder, result);
                }
            }

            // Download video and audio sources
            Elements mediaElements = doc.select("video source[src], audio source[src], video[src], audio[src]");
            for (Element media : mediaElements) {
                String src = media.attr("abs:src");
                if (!src.isEmpty() && !downloadedUrls.contains(src) && isDownloadableResource(src)) {
                    downloadResource(src, "media", projectFolder, result);
                }
            }

            // Download picture sources
            Elements pictureSources = doc.select("picture source[srcset], source[srcset]");
            for (Element source : pictureSources) {
                String srcset = source.attr("abs:srcset");
                if (!srcset.isEmpty()) {
                    // Parse srcset for multiple image sources
                    String[] sources = srcset.split("\\s*,\\s*");
                    for (String src : sources) {
                        String url = src.split("\\s+")[0]; // Get URL part (before space and descriptor)
                        if (!url.isEmpty() && !downloadedUrls.contains(url) && isDownloadableResource(url)) {
                            downloadResource(url, "images", projectFolder, result);
                        }
                    }
                }
            }

            // Download background images from inline styles
            Elements elementsWithStyle = doc.select("[style*=\"url(\"]");
            for (Element element : elementsWithStyle) {
                String style = element.attr("style");
                Pattern urlPattern = Pattern.compile("url\\(['\"]?([^'\")]*)['\"]?\\)", Pattern.CASE_INSENSITIVE);
                Matcher matcher = urlPattern.matcher(style);
                while (matcher.find()) {
                    String url = matcher.group(1);
                    if (!url.startsWith("data:") && !url.startsWith("#") && !url.isEmpty()) {
                        try {
                            URL absoluteUrl = new URL(new URL(baseUrl), url);
                            String fullUrl = absoluteUrl.toString();
                            if (!downloadedUrls.contains(fullUrl) && isDownloadableResource(fullUrl)) {
                                downloadResource(fullUrl, "images", projectFolder, result);
                            }
                        } catch (MalformedURLException e) {
                            // Skip invalid URLs
                        }
                    }
                }
            }

            // Download font files from CSS @font-face and link elements
            Elements fontLinks = doc.select(
                    "link[href*='.woff'], link[href*='.woff2'], link[href*='.ttf'], link[href*='.eot'], link[href*='.otf']");
            for (Element font : fontLinks) {
                String href = font.attr("abs:href");
                if (!href.isEmpty() && !downloadedUrls.contains(href) && isDownloadableResource(href)) {
                    downloadResource(href, "fonts", projectFolder, result);
                }
            }

        } catch (Exception e) {
            System.err.println("Error downloading assets: " + e.getMessage());
        }
    }

    private static boolean isDownloadableResource(String url) {
        // Skip data URLs, anchor links, and obviously non-downloadable resources
        return !url.startsWith("data:") &&
                !url.startsWith("#") &&
                !url.startsWith("javascript:") &&
                !url.startsWith("mailto:");
    }

    private static void downloadResource(String url, String type, File projectFolder, DownloadResult result) {
        try {
            if (downloadedUrls.contains(url)) {
                return; // Skip duplicates
            }

            File targetFolder = getTargetFolder(type, projectFolder);
            String fileName = getFileNameFromUrl(url, type, getFileExtension(url));
            File outputFile = new File(targetFolder, fileName);

            byte[] data = downloadBinaryAsset(url);
            if (data != null && data.length > 0) {
                Files.write(outputFile.toPath(), data);
                downloadedUrls.add(url);
                urlToLocalPath.put(url, type + "/" + fileName);

                result.totalFiles++;
                result.totalSize += data.length;

                System.out.println("✅ Downloaded: " + url + " -> " + outputFile.getName());
            }
        } catch (Exception e) {
            System.err.println("❌ Failed to download " + type + ": " + url + " - " + e.getMessage());
        }
    }

    private static File getTargetFolder(String type, File projectFolder) {
        switch (type.toLowerCase()) {
            case "css":
                return new File(projectFolder, "css");
            case "js":
                return new File(projectFolder, "js");
            case "images":
                return new File(projectFolder, "images");
            case "fonts":
                return new File(projectFolder, "fonts");
            case "media":
                return new File(projectFolder, "media");
            default:
                return new File(projectFolder, "other");
        }
    }

    private static String processHtmlForLocal(Document doc, String baseUrl, File projectFolder) {
        // Process CSS links
        Elements cssLinks = doc.select("link[rel=stylesheet]");
        for (Element css : cssLinks) {
            String href = css.attr("abs:href");
            if (!href.isEmpty() && urlToLocalPath.containsKey(href)) {
                css.attr("href", urlToLocalPath.get(href));
            }
        }

        // Process JS scripts
        Elements jsScripts = doc.select("script[src]");
        for (Element script : jsScripts) {
            String src = script.attr("abs:src");
            if (!src.isEmpty() && urlToLocalPath.containsKey(src)) {
                script.attr("src", urlToLocalPath.get(src));
            }
        }

        // Process images
        Elements images = doc.select("img[src]");
        for (Element img : images) {
            String src = img.attr("abs:src");
            if (!src.isEmpty() && urlToLocalPath.containsKey(src)) {
                img.attr("src", urlToLocalPath.get(src));
            }
        }

        // Process icons
        Elements icons = doc.select("link[rel~=icon], link[rel~=apple-touch-icon]");
        for (Element icon : icons) {
            String href = icon.attr("abs:href");
            if (!href.isEmpty() && urlToLocalPath.containsKey(href)) {
                icon.attr("href", urlToLocalPath.get(href));
            }
        }

        // Process media elements
        Elements mediaElements = doc.select("video source[src], audio source[src], video[src], audio[src]");
        for (Element media : mediaElements) {
            String src = media.attr("abs:src");
            if (!src.isEmpty() && urlToLocalPath.containsKey(src)) {
                media.attr("src", urlToLocalPath.get(src));
            }
        }

        // Process picture sources
        Elements pictureSources = doc.select("picture source[srcset], source[srcset]");
        for (Element source : pictureSources) {
            String srcset = source.attr("srcset");
            if (!srcset.isEmpty()) {
                String newSrcset = convertSrcsetToLocal(srcset);
                source.attr("srcset", newSrcset);
            }
        }

        // Process inline styles with background images
        Elements elementsWithStyle = doc.select("[style*=\"url(\"]");
        for (Element element : elementsWithStyle) {
            String style = element.attr("style");
            Pattern urlPattern = Pattern.compile("url\\(['\"]?([^'\")]*)['\"]?\\)", Pattern.CASE_INSENSITIVE);
            Matcher matcher = urlPattern.matcher(style);
            StringBuilder newStyle = new StringBuilder();
            while (matcher.find()) {
                String url = matcher.group(1);
                if (urlToLocalPath.containsKey(url)) {
                    matcher.appendReplacement(newStyle, "url('" + urlToLocalPath.get(url) + "')");
                } else {
                    matcher.appendReplacement(newStyle, matcher.group(0));
                }
            }
            matcher.appendTail(newStyle);
            element.attr("style", newStyle.toString());
        }

        // Process font links
        Elements fontLinks = doc.select(
                "link[href*='.woff'], link[href*='.woff2'], link[href*='.ttf'], link[href*='.eot'], link[href*='.otf']");
        for (Element font : fontLinks) {
            String href = font.attr("abs:href");
            if (!href.isEmpty() && urlToLocalPath.containsKey(href)) {
                font.attr("href", urlToLocalPath.get(href));
            }
        }

        // Add base tag to handle relative paths
        if (doc.select("base").isEmpty()) {
            Element base = doc.createElement("base");
            base.attr("href", "./");
            doc.head().prependChild(base);
        }

        return doc.outerHtml();
    }

    private static String convertSrcsetToLocal(String srcset) {
        String[] sources = srcset.split("\\s*,\\s*");
        StringBuilder newSrcset = new StringBuilder();

        for (String source : sources) {
            String[] parts = source.trim().split("\\s+");
            if (parts.length > 0) {
                String url = parts[0];
                if (urlToLocalPath.containsKey(url)) {
                    newSrcset.append(urlToLocalPath.get(url));
                } else {
                    newSrcset.append(url);
                }

                // Add the descriptor (width or density) if present
                for (int i = 1; i < parts.length; i++) {
                    newSrcset.append(" ").append(parts[i]);
                }
                newSrcset.append(", ");
            }
        }

        // Remove trailing comma and space
        if (newSrcset.length() > 2) {
            newSrcset.setLength(newSrcset.length() - 2);
        }

        return newSrcset.toString();
    }

    private static void createSourceCodeFile(File projectFolder, Document doc, String url, int fileCount)
            throws IOException {
        HtmlStorage sourceContent = new HtmlStorage();

        sourceContent.appendLine("=".repeat(80));
        sourceContent.appendLine("COMPREHENSIVE WEBSITE SOURCE CODE");
        sourceContent.appendLine("=".repeat(80));

        sourceContent.appendLine("WEBSITE INFORMATION:");
        sourceContent.appendLine("-".repeat(40));
        sourceContent.appendLine("URL: " + url);
        sourceContent.appendLine(
                "Downloaded: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        sourceContent.appendLine("Total Files: " + fileCount);

        // HTML Structure
        sourceContent.appendLine("HTML STRUCTURE:");
        sourceContent.appendLine("-".repeat(40));
        sourceContent.appendLine(formatHtmlForText(doc.html()));

        // CSS Content
        sourceContent.appendLine("CSS STYLES:");
        sourceContent.appendLine("-".repeat(40));
        Elements styleTags = doc.select("style");
        for (Element style : styleTags) {
            sourceContent.appendLine(style.html());

        }

        // JavaScript Content
        sourceContent.appendLine("JAVASCRIPT CODE:");
        sourceContent.appendLine("-".repeat(40));
        Elements scriptTags = doc.select("script:not([src])");
        for (Element script : scriptTags) {
            sourceContent.appendLine(script.html());

        }

        // Meta Information
        sourceContent.appendLine("META INFORMATION:");
        sourceContent.appendLine("-".repeat(40));
        Elements metaTags = doc.select("meta");
        for (Element meta : metaTags) {
            String name = meta.attr("name");
            String property = meta.attr("property");
            String content = meta.attr("content");
            if (!name.isEmpty()) {
                sourceContent.appendLine(name + ": " + content);
            } else if (!property.isEmpty()) {
                sourceContent.appendLine(property + ": " + content);
            }
        }

        // Resources List
        sourceContent.appendLine("DOWNLOADED RESOURCES:");
        sourceContent.appendLine("-".repeat(40));
        for (Map.Entry<String, String> entry : urlToLocalPath.entrySet()) {
            sourceContent.appendLine(entry.getKey() + " -> " + entry.getValue());
        }

        Files.write(new File(projectFolder, "full_source_code.txt").toPath(),
                sourceContent.get().getBytes(StandardCharsets.UTF_8));
    }

    private static String formatHtmlForText(String html) {
        // Basic HTML formatting for readability
        return html
                .replace("><", ">\n<")
                .replace("/>", "/>\n")
                .replace("</", "\n</")
                .replaceAll("(\\s{2,})", " ")
                .replaceAll("\\n\\s*\\n", "\n")
                .trim();
    }

    private static byte[] downloadBinaryAsset(String url) {
        try {
            return Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .ignoreContentType(true)
                    .ignoreHttpErrors(true)
                    .maxBodySize(0) // No size limit
                    .execute()
                    .bodyAsBytes();
        } catch (Exception e) {
            System.err.println("Failed to download: " + url + " - " + e.getMessage());
            return null;
        }
    }

    private static String getFileNameFromUrl(String url, String prefix, String extension) {
        try {
            URL urlObj = new URL(url);
            String path = urlObj.getPath();
            if (path != null && !path.isEmpty() && !path.equals("/")) {
                String fileName = path.substring(path.lastIndexOf('/') + 1);
                if (fileName.isEmpty() || !fileName.contains(".")) {
                    return prefix + "_" + System.currentTimeMillis() + "." + extension;
                }
                // Clean filename and preserve extension
                String nameWithoutExt = fileName.substring(0, fileName.lastIndexOf('.'));
                String ext = fileName.substring(fileName.lastIndexOf('.') + 1);
                nameWithoutExt = nameWithoutExt.replaceAll("[^a-zA-Z0-9._-]", "_");
                return nameWithoutExt + "." + ext;
            }
        } catch (Exception e) {
            // Fall through to default naming
        }
        return prefix + "_" + System.currentTimeMillis() + "." + extension;
    }

    private static String getFileExtension(String url) {
        if (url.contains(".")) {
            String path = url.split("[?#]")[0]; // Remove query parameters and fragments
            String ext = path.substring(path.lastIndexOf('.') + 1).toLowerCase();
            if (ext.length() <= 6 && !ext.contains("/")) {
                return ext;
            }
        }

        // Determine extension from URL patterns and content type
        if (url.contains(".css"))
            return "css";
        if (url.contains(".js"))
            return "js";
        if (url.contains(".png"))
            return "png";
        if (url.contains(".jpg") || url.contains(".jpeg"))
            return "jpg";
        if (url.contains(".gif"))
            return "gif";
        if (url.contains(".svg"))
            return "svg";
        if (url.contains(".webp"))
            return "webp";
        if (url.contains(".ico"))
            return "ico";
        if (url.contains(".woff"))
            return "woff";
        if (url.contains(".woff2"))
            return "woff2";
        if (url.contains(".ttf"))
            return "ttf";
        if (url.contains(".eot"))
            return "eot";
        if (url.contains(".otf"))
            return "otf";
        if (url.contains(".mp4"))
            return "mp4";
        if (url.contains(".webm"))
            return "webm";
        if (url.contains(".mp3"))
            return "mp3";
        if (url.contains(".wav"))
            return "wav";

        return "bin"; // Default extension
    }

    private static void createStructurePrompt(File projectFolder, String domain, String originalUrl, int fileCount)
            throws IOException {
        String promptContent = "WEBSITE STRUCTURE PROMPT\n" +
                "========================\n\n" +
                "Website: " + domain + "\n" +
                "Original URL: " + originalUrl + "\n" +
                "Downloaded: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n" +
                "Total Files: " + fileCount + "\n\n" +
                "FOLDER STRUCTURE:\n" +
                "----------------\n" +
                projectFolder.getName() + "/\n" +
                "├── index.html              # Main HTML file\n" +
                "├── full_source_code.txt    # Complete source code as text\n" +
                "├── css/                    # All CSS stylesheets\n" +
                "├── js/                     # All JavaScript files\n" +
                "├── images/                 # All images (png, jpg, svg, webp, etc.)\n" +
                "├── fonts/                  # Font files (woff, woff2, ttf, eot, otf)\n" +
                "├── media/                  # Video and audio files\n" +
                "├── other/                  # Other resources\n" +
                "├── structure_prompt.txt    # This file\n" +
                "└── README.md               # Project documentation\n\n" +
                "HOW TO USE LOCALLY:\n" +
                "------------------\n" +
                "1. Open 'index.html' in any modern web browser\n" +
                "2. The website should display exactly as the original\n" +
                "3. All resources are loaded from local folders\n" +
                "4. Works with both file:// protocol and localhost\n\n" +
                "VIEW SOURCE CODE:\n" +
                "----------------\n" +
                "Open 'full_source_code.txt' to see the complete, formatted source code\n\n" +
                "MODIFICATION INSTRUCTIONS:\n" +
                "------------------------\n" +
                "Layout: Edit HTML structure in index.html\n" +
                "Design: Modify CSS files in css/ folder\n" +
                "Scripts: Update JavaScript in js/ folder\n" +
                "Images: Replace files in images/ folder\n" +
                "Fonts: Update font files in fonts/ folder";

        Files.write(new File(projectFolder, "structure_prompt.txt").toPath(),
                promptContent.getBytes(StandardCharsets.UTF_8));
    }

    private static void createReadme(File projectFolder, String domain, String originalUrl) throws IOException {
        String readmeContent = "# " + domain + " - Local Copy\n\n" +
                "This is a complete local copy of the website downloaded using Web Scraper App.\n\n" +
                "## Project Information\n" +
                "- **Original URL**: " + originalUrl + "\n" +
                "- **Download Date**: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                + "\n" +
                "- **Local File**: `index.html`\n\n" +
                "## How to Use\n" +
                "1. Open `index.html` in your web browser\n" +
                "2. The website should display exactly as the original\n" +
                "3. All assets (CSS, JS, images, fonts, media) are stored locally\n\n" +
                "## Source Code\n" +
                "The complete source code is available in `full_source_code.txt` with:\n" +
                "- Formatted HTML structure\n" +
                "- CSS styles (inline and external)\n" +
                "- JavaScript code\n" +
                "- Meta information\n" +
                "- Downloaded resources list\n\n" +
                "## Folder Structure\n" +
                "```\n" +
                projectFolder.getName() + "/\n" +
                "├── index.html\n" +
                "├── full_source_code.txt\n" +
                "├── css/\n" +
                "├── js/\n" +
                "├── images/\n" +
                "├── fonts/\n" +
                "├── media/\n" +
                "├── other/\n" +
                "├── structure_prompt.txt\n" +
                "└── README.md\n" +
                "```\n\n" +
                "## Features\n" +
                "- ✅ Complete HTML structure preservation\n" +
                "- ✅ All CSS and JavaScript downloaded\n" +
                "- ✅ Images, fonts, and media preserved\n" +
                "- ✅ Local paths updated for offline use\n" +
                "- ✅ No external dependencies (pure Jsoup + Java)\n" +
                "- ✅ Duplicate download prevention\n" +
                "- ✅ Background images and fonts handled\n" +
                "- ✅ Responsive images (srcset) supported";

        Files.write(new File(projectFolder, "README.md").toPath(),
                readmeContent.getBytes(StandardCharsets.UTF_8));
    }
}

// ============================================================================
// WEB SCRAPER ENGINE (NO SELENIUM)
// ============================================================================

/**
 * Core web scraping engine using jsoup library.
 * Handles HTML extraction, parsing, and data extraction without Selenium.
 */
class WebScraper {
    private static final int TIMEOUT_MS = 30000; // 30 seconds
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";

    public static class ScrapedData {
        public String title = "";
        public String description = "";
        public String keywords = "";
        public List<String> links = new ArrayList<>();
        public List<String> images = new ArrayList<>();
        // Separated content fields for clearer structure
        public String rawHtml = ""; // Raw HTTP response body
        public String parsedHtml = ""; // HTML after Jsoup parsing
        public String inlineCss = "";
        public String inlineJs = "";
        public List<String> externalCss = new ArrayList<>();
        public List<String> externalJs = new ArrayList<>();
        public Map<String, String> externalCssContent = new HashMap<>();
        public Map<String, String> externalJsContent = new HashMap<>();
        public List<String> assets = new ArrayList<>();
        public String htmlContent = "";
        public String textContent = "";
        public int statusCode = 0;
        public long fetchTimeMs = 0;
    }

    public static ScrapedData scrapeWebsite(String urlString) throws Exception {
        ScrapedData data = new ScrapedData();
        long startTime = System.currentTimeMillis();

        try {
            // Validate URL
            if (!UrlValidator.isValid(urlString)) {
                throw new IllegalArgumentException("Invalid URL format: " + urlString);
            }

            // Get HTML using Jsoup (no JavaScript rendering)
            Connection.Response response = Jsoup
                    .connect(urlString)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .followRedirects(true)
                    .ignoreHttpErrors(true)
                    .execute();

            String rawBody = response.body();
            Document doc = response.parse();
            data.rawHtml = rawBody != null ? rawBody : "";
            data.parsedHtml = doc.html();
            data.statusCode = response.statusCode();

            // Extract title
            Element titleElement = doc.selectFirst("title");
            if (titleElement != null) {
                data.title = titleElement.text();
            }

            // Extract meta description
            Element descElement = doc.selectFirst("meta[name=description]");
            if (descElement != null) {
                data.description = descElement.attr("content");
            }

            // Extract meta keywords
            Element keywordElement = doc.selectFirst("meta[name=keywords]");
            if (keywordElement != null) {
                data.keywords = keywordElement.attr("content");
            }

            // Extract all links
            Elements links = doc.select("a[href]");
            for (Element link : links) {
                String href = link.attr("abs:href");
                if (!href.isEmpty()) {
                    data.links.add(href);
                }
            }

            // Extract all images
            Elements images = doc.select("img[src]");
            for (Element img : images) {
                String src = img.attr("abs:src");
                if (!src.isEmpty()) {
                    data.images.add(src);
                    data.assets.add(src);
                }
            }

            // Inline CSS blocks
            Elements styleTags = doc.select("style");
            StringBuilder inlineCss = new StringBuilder();
            for (Element s : styleTags) {
                inlineCss.append(s.html()).append("\n\n");
            }
            data.inlineCss = inlineCss.toString();

            // Inline JS blocks
            Elements inlineScripts = doc.select("script:not([src])");
            StringBuilder inlineJs = new StringBuilder();
            for (Element s : inlineScripts) {
                inlineJs.append(s.html()).append("\n\n");
            }
            data.inlineJs = inlineJs.toString();

            // External CSS and JS references (lists)
            Elements cssLinks = doc.select("link[rel=stylesheet]");
            for (Element css : cssLinks) {
                String href = css.attr("abs:href");
                if (!href.isEmpty()) {
                    data.externalCss.add(href);
                    data.assets.add(href);
                }
            }
            Elements scriptTags = doc.select("script[src]");
            for (Element script : scriptTags) {
                String src = script.attr("abs:src");
                if (!src.isEmpty()) {
                    data.externalJs.add(src);
                    data.assets.add(src);
                }
            }

            // Get full HTML content
            data.htmlContent = buildComprehensiveSource(doc, urlString, data);

            // Get text content
            data.textContent = doc.text();

        } catch (Exception e) {
            throw new RuntimeException("Error scraping website: " + e.getMessage(), e);
        }

        data.fetchTimeMs = System.currentTimeMillis() - startTime;
        return data;
    }

    /**
     * Builds a comprehensive, well-structured source code output
     */
    private static String buildComprehensiveSource(Document doc, String baseUrl, ScrapedData data) {
        HtmlStorage output = new HtmlStorage();

        output.appendLine("╔════════════════════════════════════════════════════════════════════════════════╗");
        output.appendLine("║                    COMPREHENSIVE WEBSITE SOURCE CODE                         ║");
        output.appendLine("╚════════════════════════════════════════════════════════════════════════════════╝\n");

        output.appendLine("🌐 WEBSITE INFORMATION");
        output.appendLine("════════════════════════════════════════════════════════════════════════════════");
        output.appendLine("URL: " + baseUrl);
        output.appendLine("Title: " + data.title);
        output.appendLine("Description: " + data.description);
        output.appendLine("Keywords: " + data.keywords);
        output.appendLine("Status Code: " + data.statusCode);
        output.appendLine("");

        // HTML Section
        output.appendLine("\n\n📄 HTML SOURCE CODE");
        output.appendLine("════════════════════════════════════════════════════════════════════════════════");
        output.appendLine(formatHtmlForDisplay(doc.html()));

        // CSS Section
        output.appendLine("\n\n🎨 CSS STYLES");
        output.appendLine("════════════════════════════════════════════════════════════════════════════════");
        Elements styleTags = doc.select("style");
        if (styleTags.isEmpty()) {
            output.appendLine("(No inline styles)");
        } else {
            for (Element style : styleTags) {
                output.appendLine(style.html());
                output.appendLine("");
            }
        }

        // JavaScript Section
        output.appendLine("\n\n⚙️  JAVASCRIPT CODE");
        output.appendLine("════════════════════════════════════════════════════════════════════════════════");
        Elements scriptTags = doc.select("script:not([src])");
        if (scriptTags.isEmpty()) {
            output.appendLine("(No inline JavaScript)");
        } else {
            for (Element script : scriptTags) {
                output.appendLine(script.html());
                output.appendLine("");
            }
        }

        // Resources Summary
        output.appendLine("\n\n📁 RESOURCES SUMMARY");
        output.appendLine("════════════════════════════════════════════════════════════════════════════════");
        output.appendLine("Total Links: " + data.links.size());
        output.appendLine("Total Images: " + data.images.size());
        output.appendLine("External CSS Files: " + data.externalCss.size());
        output.appendLine("External JS Files: " + data.externalJs.size());

        return output.get();
    }

    private static String formatHtmlForDisplay(String html) {
        // Add basic formatting for better readability in text display
        return html
                .replace("><", ">\n<")
                .replace("/>", "/>\n")
                .replace("</", "\n</")
                .replaceAll("(\\s{2,})", " ")
                .replaceAll("\\n\\s*\\n", "\n")
                .trim();
    }

    /**
     * Performs BFS crawl to find all links (one level only).
     */
    public static QueueDSA<String> crawlLinks(String urlString) throws Exception {
        QueueDSA<String> linkQueue = new QueueDSA<>();
        ScrapedData data = scrapeWebsite(urlString);

        for (String link : data.links) {
            linkQueue.enqueue(link);
        }

        return linkQueue;
    }
}

// ============================================================================
// OPENROUTER AI CLIENT
// ============================================================================

/**
 * Integrates OpenRouter API for AI-powered HTML summarization.
 * Uses HttpURLConnection and Gson for JSON handling.
 */
class OpenRouterClient {
    private static final String API_URL = "https://openrouter.ai/api/v1/chat/completions";
    // TODO: Replace with your actual OpenRouter API key from
    // https://openrouter.ai/keys
    // Or set OPENROUTER_API_KEY environment variable
    private static final String API_KEY = System.getenv("OPENROUTER_API_KEY") != null
            ? System.getenv("OPENROUTER_API_KEY")
            : ""; // Add your API key here

    private static final String MODEL = "nvidia/nemotron-nano-12b-v2-vl:free";
    private static final int TIMEOUT_MS = 30000;

    public static class AnalysisResult {
        public String summary = "";
        public String insights = "";
        public long timeMs = 0;
        public boolean success = false;
        public String error = "";
    }

    public static AnalysisResult analyzeHtml(String htmlContent) {
        AnalysisResult result = new AnalysisResult();
        long startTime = System.currentTimeMillis();

        try {
            if (htmlContent == null || htmlContent.isEmpty()) {
                result.error = "HTML content is empty";
                return result;
            }

            // Limit content to prevent token overflow
            String content = htmlContent.length() > 5000 ? htmlContent.substring(0, 5000) + "..." : htmlContent;

            // Create request JSON manually (no external JSON library needed)
            String requestJson = buildJsonRequest(content);

            // Send request
            @SuppressWarnings("deprecation")
            URL url = new URL(API_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + API_KEY);
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setDoOutput(true);

            // Write request body
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = requestJson.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // Read response
            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                result.error = "API Error " + responseCode + ": Check API key or rate limits";
                return result;
            }

            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
            }

            // Parse response manually
            String assistantMessage = parseJsonResponse(response.toString());
            if (assistantMessage != null && !assistantMessage.isEmpty()) {
                result.summary = assistantMessage;
                result.success = true;
            } else {
                result.error = "Failed to extract content from API response";
            }

        } catch (Exception e) {
            result.error = "Analysis failed: " + e.getMessage();
        }

        result.timeMs = System.currentTimeMillis() - startTime;
        return result;
    }

    private static String buildJsonRequest(String content) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"model\":\"").append(MODEL).append("\",");
        json.append("\"messages\":[");
        json.append("{");
        json.append("\"role\":\"user\",");
        json.append("\"content\":\"").append(escapeJsonString(
                "Analyze this HTML content and provide a summary with key insights:\n\n" + content))
                .append("\"");
        json.append("}");
        json.append("],");
        json.append("\"max_tokens\":500,");
        json.append("\"temperature\":0.7");
        json.append("}");
        return json.toString();
    }

    private static String escapeJsonString(String text) {
        if (text == null)
            return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String parseJsonResponse(String jsonResponse) {
        try {
            // Find "choices" array
            int choicesIndex = jsonResponse.indexOf("\"choices\"");
            if (choicesIndex == -1)
                return null;

            // Find the first message content
            int contentIndex = jsonResponse.indexOf("\"content\"", choicesIndex);
            if (contentIndex == -1)
                return null;

            // Find the value after "content\":"
            int startQuote = jsonResponse.indexOf("\"", contentIndex + 10);
            if (startQuote == -1)
                return null;

            int endQuote = jsonResponse.indexOf("\"", startQuote + 1);
            if (endQuote == -1)
                return null;

            String content = jsonResponse.substring(startQuote + 1, endQuote);

            // Unescape JSON string
            return unescapeJsonString(content);
        } catch (Exception e) {
            return null;
        }
    }

    private static String unescapeJsonString(String text) {
        if (text == null)
            return "";
        return text.replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\r", "\r")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    public static boolean isConfigured() {
        // Consider configured if API_KEY is not the placeholder value
        String placeholder = "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";
        return API_KEY != null && !API_KEY.isEmpty() && !API_KEY.equals(placeholder);
    }
}

// ============================================================================
// MODERN SWING GUI - REDESIGNED
// ============================================================================

/**
 * Professional, modern GUI built with Swing components.
 * Features: Modern design, responsive layout, multi-threaded scraping.
 */
public class WebScraperApp extends JFrame {
    private JTextField urlField;
    private JTextArea contentArea;
    // Source sub-areas (separated views)
    private JTextArea htmlRawArea; // Raw HTTP HTML
    private JTextArea parsedHtmlArea; // Parsed HTML (no JavaScript rendering)
    private JTextArea cssArea; // Inline + external CSS contents
    private JTextArea jsArea; // Inline + external JS contents
    private JTextArea resourcesArea; // Assets, links, headers
    private JTextArea logArea;
    private JButton scrapeButton;
    private JButton saveButton;
    private JButton copyButton;
    private JButton downloadFullSiteButton;
    private JLabel statusLabel;
    private JLabel timerLabel;
    private JProgressBar progressBar;

    private boolean isDarkTheme = false;
    private long scrapeStartTime = 0;
    private javax.swing.Timer updateTimer;
    // Store last fetched full HTML source
    private String lastHtmlContent = "";
    private WebScraper.ScrapedData lastScrapedData;

    // Modern color scheme - LIGHT MODE
    private static final Color MODERN_BG = new Color(248, 250, 252);
    private static final Color MODERN_PANEL = new Color(255, 255, 255);
    private static final Color MODERN_TEXT = new Color(51, 51, 51);
    private static final Color MODERN_BORDER = new Color(30, 144, 255); // #1E90FF
    private static final Color MODERN_ACCENT = new Color(30, 144, 255);
    private static final Color MODERN_SUCCESS = new Color(76, 175, 80);
    private static final Color MODERN_ERROR = new Color(244, 67, 54);
    private static final Color MODERN_INPUT_BG = new Color(248, 248, 248);

    // Modern color scheme - DARK MODE
    private static final Color DARK_BG = new Color(30, 30, 35);
    private static final Color DARK_PANEL = new Color(45, 45, 50);
    private static final Color DARK_TEXT = new Color(230, 230, 235);
    private static final Color DARK_BORDER = new Color(65, 105, 225);
    private static final Color DARK_INPUT_BG = new Color(60, 60, 65);

    // Modern fonts
    private static final Font FONT_PRIMARY = new Font("Segoe UI", Font.PLAIN, 13);
    private static final Font FONT_BOLD = new Font("Segoe UI", Font.BOLD, 13);
    private static final Font FONT_MONO = new Font("Consolas", Font.PLAIN, 12);
    private static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 16);

    public WebScraperApp() {
        initializeFrame();
        createUI();
        applyTheme(isDarkTheme);
        setVisible(true);
    }

    private void initializeFrame() {
        setTitle("Web Scraper - Static Website Downloader");
        setSize(1200, 800);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(true);
        setMinimumSize(new Dimension(900, 650));

        // Set application icon
        try {
            setIconImage(Toolkit.getDefaultToolkit().getImage(getClass().getResource("/icon.png")));
        } catch (Exception e) {
            // Use default if icon not found
        }
    }

    private void createUI() {
        // Main container with modern styling
        JPanel mainPanel = new JPanel(new BorderLayout(15, 15));
        mainPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        mainPanel.setBackground(MODERN_BG);

        // Header panel with URL input
        mainPanel.add(createHeaderPanel(), BorderLayout.NORTH);

        // Content area (tabbed) - center
        mainPanel.add(createContentPanel(), BorderLayout.CENTER);

        // Button panel - south
        mainPanel.add(createButtonPanel(), BorderLayout.SOUTH);

        setContentPane(mainPanel);
    }

    private JPanel createHeaderPanel() {
        JPanel headerPanel = new JPanel(new BorderLayout(15, 0));
        headerPanel.setBackground(MODERN_BG);
        headerPanel.setBorder(new EmptyBorder(0, 0, 15, 0));

        // App title
        JLabel titleLabel = new JLabel(" Static Website Downloader");
        titleLabel.setFont(FONT_TITLE);
        titleLabel.setForeground(MODERN_TEXT);

        // URL input section
        JPanel urlPanel = new JPanel(new BorderLayout(10, 0));
        urlPanel.setBackground(MODERN_BG);

        JLabel urlLabel = new JLabel("Enter Website URL:");
        urlLabel.setFont(FONT_BOLD);
        urlLabel.setForeground(MODERN_TEXT);

        urlField = createModernTextField("https://example.com");

        // Control buttons (Scrape, Theme toggle)
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        controlPanel.setBackground(MODERN_BG);

        scrapeButton = createModernButton(" Scrape Website", MODERN_SUCCESS);
        scrapeButton.addActionListener(e -> performScraping());

        JButton themeButton = createModernButton(isDarkTheme ? " Light" : " Dark", MODERN_ACCENT);
        themeButton.addActionListener(e -> toggleTheme());

        controlPanel.add(scrapeButton);
        controlPanel.add(themeButton);

        urlPanel.add(urlLabel, BorderLayout.WEST);
        urlPanel.add(urlField, BorderLayout.CENTER);
        urlPanel.add(controlPanel, BorderLayout.EAST);

        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.add(urlPanel, BorderLayout.CENTER);

        return headerPanel;
    }

    private JComponent createContentPanel() {
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(FONT_BOLD);
        tabbedPane.setBackground(MODERN_PANEL);
        tabbedPane.setForeground(MODERN_TEXT);

        // Apply modern tab styling
        UIManager.put("TabbedPane.background", MODERN_PANEL);
        UIManager.put("TabbedPane.foreground", MODERN_TEXT);
        UIManager.put("TabbedPane.selected", MODERN_ACCENT);

        // Content tab
        JPanel contentPanel = new JPanel(new BorderLayout(10, 10));
        contentPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        contentPanel.setBackground(MODERN_PANEL);

        contentArea = createModernTextArea();
        JScrollPane contentScroll = new JScrollPane(contentArea);
        contentScroll.setBorder(createModernBorder());
        contentScroll.getVerticalScrollBar().setUnitIncrement(16);

        progressBar = new JProgressBar();
        progressBar.setIndeterminate(false);
        progressBar.setVisible(false);
        progressBar.setPreferredSize(new Dimension(0, 4));
        progressBar.setForeground(MODERN_ACCENT);

        contentPanel.add(progressBar, BorderLayout.NORTH);
        contentPanel.add(contentScroll, BorderLayout.CENTER);

        // Source tab with multiple sub-tabs
        JPanel sourcePanel = new JPanel(new BorderLayout(10, 10));
        sourcePanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        sourcePanel.setBackground(MODERN_PANEL);

        JTabbedPane sourceTabs = new JTabbedPane();
        sourceTabs.setFont(FONT_PRIMARY);

        // Raw HTTP HTML
        htmlRawArea = createModernTextArea();
        htmlRawArea.setFont(FONT_MONO);
        JScrollPane rawScroll = new JScrollPane(htmlRawArea);
        rawScroll.setBorder(createModernBorder());
        rawScroll.getVerticalScrollBar().setUnitIncrement(16);
        sourceTabs.addTab(" HTML Raw", rawScroll);

        // Parsed HTML (no JavaScript rendering)
        parsedHtmlArea = createModernTextArea();
        parsedHtmlArea.setFont(FONT_MONO);
        JScrollPane parsedScroll = new JScrollPane(parsedHtmlArea);
        parsedScroll.setBorder(createModernBorder());
        parsedScroll.getVerticalScrollBar().setUnitIncrement(16);
        sourceTabs.addTab(" HTML Parsed", parsedScroll);

        // CSS (inline + fetched externals)
        cssArea = createModernTextArea();
        cssArea.setFont(FONT_MONO);
        JScrollPane cssScroll = new JScrollPane(cssArea);
        cssScroll.setBorder(createModernBorder());
        cssScroll.getVerticalScrollBar().setUnitIncrement(16);
        sourceTabs.addTab(" CSS", cssScroll);

        // JavaScript (inline + fetched externals)
        jsArea = createModernTextArea();
        jsArea.setFont(FONT_MONO);
        JScrollPane jsScroll = new JScrollPane(jsArea);
        jsScroll.setBorder(createModernBorder());
        jsScroll.getVerticalScrollBar().setUnitIncrement(16);
        sourceTabs.addTab(" JavaScript", jsScroll);

        // Resources (images, fonts, links, headers)
        resourcesArea = createModernTextArea();
        resourcesArea.setFont(FONT_MONO);
        JScrollPane resScroll = new JScrollPane(resourcesArea);
        resScroll.setBorder(createModernBorder());
        resScroll.getVerticalScrollBar().setUnitIncrement(16);
        sourceTabs.addTab(" Resources", resScroll);

        sourcePanel.add(sourceTabs, BorderLayout.CENTER);

        // Log tab
        JPanel logPanel = new JPanel(new BorderLayout(10, 10));
        logPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        logPanel.setBackground(MODERN_PANEL);

        logArea = createModernTextArea();
        logArea.setFont(FONT_MONO);
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(createModernBorder());
        logScroll.getVerticalScrollBar().setUnitIncrement(16);

        logPanel.add(logScroll, BorderLayout.CENTER);

        tabbedPane.addTab(" Summary", contentPanel);
        tabbedPane.addTab(" Source Code", sourcePanel);
        tabbedPane.addTab(" Activity Log", logPanel);

        return tabbedPane;
    }

    private JPanel createButtonPanel() {
        JPanel buttonPanel = new JPanel(new BorderLayout(10, 0));
        buttonPanel.setBackground(MODERN_BG);
        buttonPanel.setBorder(new EmptyBorder(10, 0, 0, 0));

        // Action buttons
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        actionPanel.setBackground(MODERN_BG);

        saveButton = createModernButton(" Save HTML", MODERN_ACCENT);
        saveButton.addActionListener(e -> saveHtmlToFile());

        copyButton = createModernButton(" Copy", MODERN_ACCENT);
        copyButton.addActionListener(e -> copyToClipboard());

        downloadFullSiteButton = createModernButton(" Download Full Site", MODERN_SUCCESS);
        downloadFullSiteButton.addActionListener(e -> downloadFullWebsite());

        JButton clearButton = createModernButton(" Clear", MODERN_ERROR);
        clearButton.addActionListener(e -> clearAll());

        actionPanel.add(saveButton);
        actionPanel.add(copyButton);
        actionPanel.add(downloadFullSiteButton);
        actionPanel.add(clearButton);

        // Status panel
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        statusPanel.setBackground(MODERN_BG);

        statusLabel = new JLabel("Status: Ready");
        statusLabel.setFont(FONT_PRIMARY);
        statusLabel.setForeground(MODERN_TEXT);

        timerLabel = new JLabel("Time: 0.00s");
        timerLabel.setFont(FONT_PRIMARY);
        timerLabel.setForeground(MODERN_TEXT);

        statusPanel.add(statusLabel);
        statusPanel.add(new JSeparator(JSeparator.VERTICAL));
        statusPanel.add(timerLabel);

        buttonPanel.add(actionPanel, BorderLayout.WEST);
        buttonPanel.add(statusPanel, BorderLayout.EAST);

        return buttonPanel;
    }

    private void performScraping() {
        String urlInput = urlField.getText().trim();

        if (urlInput.isEmpty()) {
            showModernError("Please enter a URL");
            return;
        }

        final String urlToScrape = UrlValidator.sanitize(urlInput);
        final String urlDisplay = urlToScrape;

        // Disable buttons during scraping
        scrapeButton.setEnabled(false);
        downloadFullSiteButton.setEnabled(false);
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);
        statusLabel.setText("Status: Scraping...");
        contentArea.setText("");
        logArea.setText("");
        scrapeStartTime = System.currentTimeMillis();

        // Start timer
        startTimer();

        // Multi-threaded scraping
        SwingWorker<WebScraper.ScrapedData, Void> worker = new SwingWorker<>() {
            @Override
            protected WebScraper.ScrapedData doInBackground() throws Exception {
                return WebScraper.scrapeWebsite(urlToScrape);
            }

            @Override
            protected void done() {
                progressBar.setVisible(false);
                scrapeButton.setEnabled(true);
                downloadFullSiteButton.setEnabled(true);
                stopTimer();

                try {
                    WebScraper.ScrapedData data = get();
                    lastScrapedData = data;
                    displayScrapedData(data);
                    statusLabel.setText("Status:  Success");
                    statusLabel.setForeground(MODERN_SUCCESS);
                    log(" Successfully scraped: " + urlDisplay);
                    log("  Time taken: " + data.fetchTimeMs + "ms");
                    log(" Links found: " + data.links.size());
                    log("  Images found: " + data.images.size());
                    log(" External CSS: " + data.externalCss.size());
                    log("  External JS: " + data.externalJs.size());
                } catch (Exception e) {
                    statusLabel.setText("Status:  Error");
                    statusLabel.setForeground(MODERN_ERROR);
                    showModernError("Scraping failed:\n" + e.getMessage());
                    log(" Error: " + e.getMessage());
                }
            }
        };

        worker.execute();
    }

    private void displayScrapedData(WebScraper.ScrapedData data) {
        HtmlStorage storage = new HtmlStorage();

        storage.appendLine("╔════════════════════════════════════════════════╗");
        storage.appendLine("║           WEB SCRAPER RESULTS                 ║");
        storage.appendLine("╚════════════════════════════════════════════════╝\n");

        storage.appendLine("📌 TITLE:");
        storage.appendLine("   " + (data.title.isEmpty() ? "[No title]" : data.title));

        storage.appendLine("\n📝 DESCRIPTION:");
        storage.appendLine("   " + (data.description.isEmpty() ? "[No description]" : data.description));

        storage.appendLine("\n🏷️  KEYWORDS:");
        storage.appendLine("   " + (data.keywords.isEmpty() ? "[No keywords]" : data.keywords));

        storage.appendLine("\n🔗 LINKS (" + data.links.size() + " found):");
        int linkCount = 0;
        for (String link : data.links) {
            if (linkCount++ < 20) {
                storage.appendLine("   └─ " + link);
            }
        }
        if (linkCount > 20) {
            storage.appendLine("   ... and " + (linkCount - 20) + " more links");
        }

        storage.appendLine("\n🖼️  IMAGES (" + data.images.size() + " found):");
        int imgCount = 0;
        for (String img : data.images) {
            if (imgCount++ < 10) {
                storage.appendLine("   └─ " + img);
            }
        }
        if (imgCount > 10) {
            storage.appendLine("   ... and " + (imgCount - 10) + " more images");
        }

        storage.appendLine("\n TEXT CONTENT (First 500 chars):");
        String textPreview = data.textContent.length() > 500 ? data.textContent.substring(0, 500) + "..."
                : data.textContent;
        storage.appendLine(textPreview);

        contentArea.setText(storage.get());

        // Store separated source information and display them in dedicated tabs
        lastHtmlContent = data.htmlContent != null ? data.htmlContent : "";

        if (htmlRawArea != null) {
            htmlRawArea.setText(data.rawHtml != null ? data.rawHtml : "");
            htmlRawArea.setCaretPosition(0);
        }
        if (parsedHtmlArea != null) {
            parsedHtmlArea.setText(data.parsedHtml != null ? formatHtml(data.parsedHtml) : "");
            parsedHtmlArea.setCaretPosition(0);
        }
        if (cssArea != null) {
            StringBuilder cssBuilder = new StringBuilder();
            cssBuilder.append("/* Inline CSS */\n\n");
            cssBuilder.append(data.inlineCss != null ? data.inlineCss : "(none)");
            cssBuilder.append("\n\n/* External CSS Files */\n");
            for (String url : data.externalCss) {
                cssBuilder.append("/* URL: ").append(url).append(" */\n");
                String c = data.externalCssContent.get(url);
                cssBuilder.append(c != null ? c : "/* failed to fetch */");
                cssBuilder.append("\n\n");
            }
            cssArea.setText(cssBuilder.toString());
            cssArea.setCaretPosition(0);
        }
        if (jsArea != null) {
            StringBuilder jsBuilder = new StringBuilder();
            jsBuilder.append("// Inline JavaScript\n\n");
            jsBuilder.append(data.inlineJs != null ? data.inlineJs : "(none)");
            jsBuilder.append("\n\n// External JS Files\n");
            for (String url : data.externalJs) {
                jsBuilder.append("// URL: ").append(url).append("\n");
                String j = data.externalJsContent.get(url);
                jsBuilder.append(j != null ? j : "// failed to fetch");
                jsBuilder.append("\n\n");
            }
            jsArea.setText(jsBuilder.toString());
            jsArea.setCaretPosition(0);
        }
        if (resourcesArea != null) {
            StringBuilder r = new StringBuilder();
            r.append("Assets (images, fonts, external files):\n");
            for (String a : data.assets) {
                r.append(" - ").append(a).append('\n');
            }
            r.append("\nLinks:\n");
            for (String l : data.links) {
                r.append(" - ").append(l).append('\n');
            }
            r.append("\nStatus code: ").append(data.statusCode).append('\n');
            resourcesArea.setText(r.toString());
            resourcesArea.setCaretPosition(0);
        }
    }

    private void saveHtmlToFile() {
        String toSave = (lastHtmlContent != null && !lastHtmlContent.isEmpty()) ? lastHtmlContent
                : contentArea.getText();

        if (toSave == null || toSave.isEmpty()) {
            showModernError("No content to save. Scrape a website first.");
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setSelectedFile(new File("scraped_content.html"));
        fileChooser.setDialogTitle("Save HTML Content");

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try {
                Files.write(file.toPath(), toSave.getBytes(StandardCharsets.UTF_8));
                showModernSuccess("Content saved to:\n" + file.getAbsolutePath());
                log(" Content saved to: " + file.getAbsolutePath());
            } catch (IOException e) {
                showModernError("Failed to save file:\n" + e.getMessage());
                log(" Save failed: " + e.getMessage());
            }
        }
    }

    private void downloadFullWebsite() {
        String urlInput = urlField.getText().trim();
        if (urlInput.isEmpty()) {
            showModernError("Please enter a URL first.");
            return;
        }

        final String urlToDownload = UrlValidator.sanitize(urlInput);

        JFileChooser dirChooser = new JFileChooser();
        dirChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        dirChooser.setDialogTitle("Select folder to save complete website");

        if (dirChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File outputDir = dirChooser.getSelectedFile();

            // Disable button during download
            downloadFullSiteButton.setEnabled(false);
            progressBar.setVisible(true);
            progressBar.setIndeterminate(true);
            statusLabel.setText("Status: Downloading full website...");

            SwingWorker<WebsiteDownloader.DownloadResult, Void> worker = new SwingWorker<>() {
                @Override
                protected WebsiteDownloader.DownloadResult doInBackground() throws Exception {
                    return WebsiteDownloader.downloadFullWebsite(urlToDownload, outputDir);
                }

                @Override
                protected void done() {
                    progressBar.setVisible(false);
                    downloadFullSiteButton.setEnabled(true);

                    try {
                        WebsiteDownloader.DownloadResult result = get();
                        if (result.success) {
                            statusLabel.setText("Status:  Website Downloaded");
                            statusLabel.setForeground(MODERN_SUCCESS);
                            showModernSuccess("Full website downloaded successfully!\n\n" +
                                    " Location: " + result.projectFolder.getAbsolutePath() + "\n" +
                                    " Files: " + result.totalFiles + " ("
                                    + String.format("%.2f", result.totalSize / 1024.0) + " KB)\n" +
                                    " Time: " + result.downloadTime + "ms\n\n" +
                                    "Open 'index.html' in browser to view the local copy.\n" +
                                    "View source code in 'full_source_code.txt'");
                            log(" Full website downloaded: " + result.message);
                        } else {
                            statusLabel.setText("Status:  Download Failed");
                            statusLabel.setForeground(MODERN_ERROR);
                            showModernError("Website download failed:\n" + result.message);
                            log(" Full website download failed: " + result.message);
                        }
                    } catch (Exception e) {
                        statusLabel.setText("Status:  Download Error");
                        statusLabel.setForeground(MODERN_ERROR);
                        showModernError("Download error: " + e.getMessage());
                        log(" Download error: " + e.getMessage());
                    }
                }
            };

            worker.execute();
        }
    }

    private void copyToClipboard() {
        String text = (lastHtmlContent != null && !lastHtmlContent.isEmpty()) ? lastHtmlContent : contentArea.getText();
        if (text == null || text.isEmpty()) {
            showModernError("No content to copy.");
            return;
        }

        StringSelection selection = new StringSelection(text);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
        showModernSuccess("Content copied to clipboard!");
        log("📋 Content copied to clipboard");
    }

    private void clearAll() {
        contentArea.setText("");
        logArea.setText("");
        urlField.setText("");
        statusLabel.setText("Status: Ready");
        statusLabel.setForeground(MODERN_TEXT);
        timerLabel.setText("Time: 0.00s");
        lastScrapedData = null;
        lastHtmlContent = "";
    }

    private void toggleTheme() {
        isDarkTheme = !isDarkTheme;
        applyTheme(isDarkTheme);
    }

    private void applyTheme(boolean dark) {
        Color bgColor = dark ? DARK_BG : MODERN_BG;
        Color fgColor = dark ? DARK_TEXT : MODERN_TEXT;
        Color panelColor = dark ? DARK_PANEL : MODERN_PANEL;
        Color borderColor = dark ? DARK_BORDER : MODERN_BORDER;
        Color inputBg = dark ? DARK_INPUT_BG : MODERN_INPUT_BG;

        // Update all components
        updateComponentColors(getContentPane(), bgColor, fgColor, panelColor, borderColor, inputBg, dark);
    }

    private void updateComponentColors(Component comp, Color bg, Color fg, Color panelBg, Color border, Color inputBg,
            boolean dark) {
        if (comp instanceof JPanel) {
            comp.setBackground(panelBg);
            comp.setForeground(fg);
        } else if (comp instanceof JTextArea || comp instanceof JTextField) {
            comp.setBackground(inputBg);
            comp.setForeground(fg);
            if (comp instanceof JTextArea) {
                ((JTextArea) comp).setCaretColor(fg);
            }
        } else if (comp instanceof JLabel) {
            comp.setForeground(fg);
        } else if (comp instanceof JButton) {
            // Keep button styling but update text color
            comp.setForeground(Color.WHITE);
        } else if (comp instanceof JTabbedPane) {
            comp.setBackground(panelBg);
            comp.setForeground(fg);
        }

        if (comp instanceof Container) {
            for (Component child : ((Container) comp).getComponents()) {
                updateComponentColors(child, bg, fg, panelBg, border, inputBg, dark);
            }
        }
    }

    private JTextField createModernTextField(String placeholder) {
        JTextField field = new JTextField(placeholder);
        field.setFont(FONT_PRIMARY);
        field.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(MODERN_BORDER, 1, true),
                new EmptyBorder(12, 15, 12, 15)));
        field.setBackground(MODERN_INPUT_BG);
        field.setForeground(MODERN_TEXT);
        field.setPreferredSize(new Dimension(400, 45));

        // Add focus listener for modern focus effect
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                field.setBorder(BorderFactory.createCompoundBorder(
                        new LineBorder(MODERN_ACCENT, 2, true),
                        new EmptyBorder(11, 14, 11, 14)));
            }

            @Override
            public void focusLost(FocusEvent e) {
                field.setBorder(BorderFactory.createCompoundBorder(
                        new LineBorder(MODERN_BORDER, 1, true),
                        new EmptyBorder(12, 15, 12, 15)));
            }
        });

        return field;
    }

    private JButton createModernButton(String text, Color bgColor) {
        JButton button = new JButton(text);
        button.setFont(FONT_BOLD);
        button.setBackground(bgColor);
        button.setForeground(Color.WHITE);
        button.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(bgColor.darker(), 1, true),
                new EmptyBorder(12, 20, 12, 20)));
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // Remove default button styling
        button.setContentAreaFilled(false);
        button.setOpaque(true);

        // Add modern hover effects
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(bgColor.brighter());
                button.setBorder(BorderFactory.createCompoundBorder(
                        new LineBorder(bgColor.brighter().darker(), 1, true),
                        new EmptyBorder(12, 20, 12, 20)));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(bgColor);
                button.setBorder(BorderFactory.createCompoundBorder(
                        new LineBorder(bgColor.darker(), 1, true),
                        new EmptyBorder(12, 20, 12, 20)));
            }

            @Override
            public void mousePressed(MouseEvent e) {
                button.setBackground(bgColor.darker());
            }
        });

        return button;
    }

    private JTextArea createModernTextArea() {
        JTextArea area = new JTextArea();
        area.setFont(FONT_MONO);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        area.setBackground(MODERN_INPUT_BG);
        area.setForeground(MODERN_TEXT);
        area.setMargin(new Insets(15, 15, 15, 15));
        area.setCaretColor(MODERN_TEXT);
        return area;
    }

    private Border createModernBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createCompoundBorder(
                        new LineBorder(MODERN_BORDER, 1, true),
                        BorderFactory.createEmptyBorder(1, 1, 1, 1)),
                BorderFactory.createEmptyBorder(0, 0, 0, 0));
    }

    private void log(String message) {
        LocalDateTime now = LocalDateTime.now();
        String timestamp = now.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        logArea.append("[" + timestamp + "] " + message + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void startTimer() {
        updateTimer = new javax.swing.Timer(100, e -> {
            long elapsed = (System.currentTimeMillis() - scrapeStartTime) / 1000;
            long millis = (System.currentTimeMillis() - scrapeStartTime) % 1000;
            timerLabel.setText(String.format("Time: %d.%02ds", elapsed, millis / 10));
        });
        updateTimer.start();
    }

    private void stopTimer() {
        if (updateTimer != null) {
            updateTimer.stop();
        }
    }

    private void showModernError(String message) {
        JOptionPane.showMessageDialog(
                this,
                message,
                "Error",
                JOptionPane.ERROR_MESSAGE);
    }

    private void showModernSuccess(String message) {
        JOptionPane.showMessageDialog(
                this,
                message,
                "Success",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private String formatHtml(String html) {
        return html.replace("><", ">\n<")
                .replace("  <", "    <");
    }

    // Main entry point
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                // Set modern look and feel
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

                // Apply modern styling to system dialogs
                UIManager.put("OptionPane.background", MODERN_PANEL);
                UIManager.put("Panel.background", MODERN_PANEL);
                UIManager.put("OptionPane.messageForeground", MODERN_TEXT);
                UIManager.put("OptionPane.messageFont", FONT_PRIMARY);

            } catch (Exception e) {
                // Use default L&F
            }
            new WebScraperApp();
        });
    }
}