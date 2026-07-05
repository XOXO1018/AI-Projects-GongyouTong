package com.gongyoutong.app.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 文档解析工具类
 * 支持：纯文本(.txt)、PDF、Word(.docx)
 */
public class DocumentParser {

    private static final String TAG = "DocumentParser";

    /** 解析结果 */
    public static class ParseResult {
        public final String text;           // 文本内容（非PDF时有值）
        public final List<Bitmap> pages;    // PDF页面图片（仅PDF时有值）
        public final int pageCount;         // PDF页数
        public final String mimeType;

        public ParseResult(String text, List<Bitmap> pages, int pageCount, String mimeType) {
            this.text = text;
            this.pages = pages;
            this.pageCount = pageCount;
            this.mimeType = mimeType;
        }

        public boolean isPdf() {
            return pages != null && !pages.isEmpty();
        }
    }

    /**
     * 解析文档内容
     * @param context Android Context
     * @param uri 文档 URI
     * @param mimeType MIME 类型
     * @return 解析结果
     */
    public static ParseResult parseDocument(Context context, Uri uri, String mimeType) throws Exception {
        if (mimeType == null) {
            mimeType = context.getContentResolver().getType(uri);
        }

        Log.d(TAG, "解析文档: mimeType=" + mimeType);

        if (mimeType == null) {
            String path = uri.getPath();
            if (path != null) {
                if (path.endsWith(".pdf")) mimeType = "application/pdf";
                else if (path.endsWith(".docx")) mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                else if (path.endsWith(".doc")) mimeType = "application/msword";
                else mimeType = "text/plain";
            }
        }

        switch (mimeType) {
            case "application/pdf":
                return parsePdf(context, uri);
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document":
                String docxText = parseDocx(context, uri);
                return new ParseResult(docxText, null, 0, mimeType);
            case "application/msword":
                throw new Exception("暂不支持 .doc 格式，请转换为 .docx 或 PDF 后重试");
            case "text/plain":
            default:
                String plainText = parsePlainText(context, uri);
                return new ParseResult(plainText, null, 0, mimeType);
        }
    }

    /**
     * 解析纯文本文件
     */
    private static String parsePlainText(Context context, Uri uri) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = context.getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString().trim();
    }

    /**
     * 解析 PDF 文件，返回每页的位图
     */
    private static ParseResult parsePdf(Context context, Uri uri) throws Exception {
        List<Bitmap> pages = new ArrayList<>();

        try (ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(uri, "r")) {
            if (pfd == null) {
                throw new Exception("无法打开PDF文件");
            }

            PdfRenderer renderer = new PdfRenderer(pfd);
            int pageCount = renderer.getPageCount();
            Log.d(TAG, "PDF 共 " + pageCount + " 页");

            for (int i = 0; i < pageCount; i++) {
                PdfRenderer.Page page = renderer.openPage(i);

                // 2x 缩放提高OCR准确率
                int width = page.getWidth() * 2;
                int height = page.getHeight() * 2;
                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(android.graphics.Color.WHITE);

                Canvas canvas = new Canvas(bitmap);
                canvas.scale(2, 2);
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

                pages.add(bitmap);
                page.close();
            }

            renderer.close();
            return new ParseResult(null, pages, pageCount, "application/pdf");
        }
    }

    /**
     * 解析 Word (.docx) 文件
     */
    private static String parseDocx(Context context, Uri uri) throws Exception {
        StringBuilder result = new StringBuilder();

        try (InputStream is = context.getContentResolver().openInputStream(uri);
             ZipInputStream zis = new ZipInputStream(is)) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();

                if ("word/document.xml".equals(name)) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[4096];
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        baos.write(buffer, 0, len);
                    }

                    String xml = baos.toString("UTF-8");
                    result.append(extractTextFromDocxXml(xml));
                    break;
                }
                zis.closeEntry();
            }
        }

        if (result.length() == 0) {
            throw new Exception("无法解析DOCX文件内容");
        }

        return result.toString().trim();
    }

    /**
     * 从 DOCX XML 中提取纯文本
     */
    private static String extractTextFromDocxXml(String xml) {
        StringBuilder text = new StringBuilder();

        // 提取段落 <w:p> 中的文本
        Pattern paragraphPattern = Pattern.compile("<w:p[^>]*>(.*?)</w:p>", Pattern.DOTALL);
        Matcher paragraphMatcher = paragraphPattern.matcher(xml);

        while (paragraphMatcher.find()) {
            String paragraph = paragraphMatcher.group(1);

            // 提取 <w:t> 标签内的文本
            Pattern textPattern = Pattern.compile("<w:t[^>]*>([^<]*)</w:t>");
            Matcher textMatcher = textPattern.matcher(paragraph);

            StringBuilder paragraphText = new StringBuilder();
            while (textMatcher.find()) {
                String content = textMatcher.group(1);
                if (content != null) {
                    paragraphText.append(content);
                }
            }

            if (paragraphText.length() > 0) {
                text.append(paragraphText).append("\n");
            }
        }

        return text.toString();
    }

    /**
     * 释放位图资源
     */
    public static void releaseBitmaps(ParseResult result) {
        if (result != null && result.pages != null) {
            for (Bitmap bm : result.pages) {
                if (bm != null && !bm.isRecycled()) {
                    bm.recycle();
                }
            }
        }
    }
}
