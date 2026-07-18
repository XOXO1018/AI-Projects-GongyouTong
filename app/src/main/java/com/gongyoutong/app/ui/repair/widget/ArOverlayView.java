package com.gongyoutong.app.ui.repair.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.gongyoutong.app.repair.FrameAnalysisResult;

import java.util.ArrayList;
import java.util.List;

/**
 * AR 叠加自定义 View — 在 CameraX PreviewView 上层绘制引导标注。
 *
 * 功能：
 * 1. 绘制高亮框（半透明填充 + 绿色虚线描边 + 文字标签）
 * 2. 绘制全屏警告横幅（红色闪烁边框）
 * 3. 坐标自适应：归一化坐标（0~1） → 像素坐标
 */
public class ArOverlayView extends View {

    /** 当前待绘制的检测区域列表 */
    private List<FrameAnalysisResult.BoundingBox> regions = new ArrayList<>();

    /** 当前警告文本（null 表示无警告） */
    private String warningText;

    /** 当前维修引导文本（null 表示不显示提示卡片） */
    private String guideText;

    // ========== 画笔 ==========

    /** 高亮框描边：绿色虚线 */
    private final Paint highlightPaint;

    /** 高亮框填充：半透明绿 */
    private final Paint highlightFillPaint;

    /** 文字标签：白色 + 黑色阴影 */
    private final Paint textPaint;

    /** 警告横幅背景：半透明红 */
    private final Paint warningBgPaint;

    /** 警告文字：白色加粗居中 */
    private final Paint warningTextPaint;

    /** 箭头画笔：黄色实线 */
    private final Paint arrowPaint;

    /** 引导卡片背景 */
    private final Paint guideBgPaint;

    // ========================================================================
    // 构造函数
    // ========================================================================

    public ArOverlayView(Context context) {
        this(context, null);
    }

    public ArOverlayView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ArOverlayView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setWillNotDraw(false);

        // 高亮框描边（绿色虚线，3px 宽）
        highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        highlightPaint.setColor(Color.GREEN);
        highlightPaint.setStyle(Paint.Style.STROKE);
        highlightPaint.setStrokeWidth(3f);
        highlightPaint.setPathEffect(new DashPathEffect(new float[]{10f, 5f}, 0f));

        // 高亮框填充（半透明绿色）
        highlightFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        highlightFillPaint.setColor(0x2200FF00);
        highlightFillPaint.setStyle(Paint.Style.FILL);

        // 文字标签（白色，带阴影）
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(36f);
        textPaint.setShadowLayer(4f, 2f, 2f, Color.BLACK);

        // 警告背景（半透明红色）
        warningBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        warningBgPaint.setColor(0xCCFF0000);
        warningBgPaint.setStyle(Paint.Style.FILL);

        // 警告文字（白色、加粗、居中）
        warningTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        warningTextPaint.setColor(Color.WHITE);
        warningTextPaint.setTextSize(48f);
        warningTextPaint.setTextAlign(Paint.Align.CENTER);
        warningTextPaint.setFakeBoldText(true);

        // 箭头（黄色实线，4px 宽）
        arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        arrowPaint.setColor(Color.YELLOW);
        arrowPaint.setStyle(Paint.Style.STROKE);
        arrowPaint.setStrokeWidth(4f);

        guideBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        guideBgPaint.setColor(0xCC111827);
        guideBgPaint.setStyle(Paint.Style.FILL);
    }

    // ========================================================================
    // 公共 API
    // ========================================================================

    /**
     * 设置需要绘制的检测区域列表。
     *
     * @param regions 归一化坐标的边界框列表，传 null 等效于空列表
     */
    public void setRegions(List<FrameAnalysisResult.BoundingBox> regions) {
        this.regions = (regions != null) ? regions : new ArrayList<FrameAnalysisResult.BoundingBox>();
        invalidate();
    }

    /**
     * 设置全屏警告文本（触发红色闪烁边框）。
     *
     * @param text 警告文本，传 null 清除警告
     */
    public void setWarning(String text) {
        this.warningText = text;
        invalidate();
    }

    /**
     * 设置画面中央的维修引导提示。
     *
     * @param text 当前步骤或下一步动作，传 null 清除提示
     */
    public void setGuideText(String text) {
        this.guideText = text;
        invalidate();
    }

    /** 清除所有标注和警告 */
    public void clear() {
        this.regions.clear();
        this.warningText = null;
        this.guideText = null;
        invalidate();
    }

    // ========================================================================
    // 绘制
    // ========================================================================

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }

        // 1. 绘制高亮框和标签
        for (FrameAnalysisResult.BoundingBox region : regions) {
            drawHighlightBox(canvas, region, w, h);
        }

        if (guideText != null && !guideText.isEmpty()) {
            drawGuideCard(canvas, w, h);
        }

        // 2. 绘制警告（最上层）
        if (warningText != null && !warningText.isEmpty()) {
            drawWarning(canvas, w, h);
        }
    }

    /**
     * 绘制单个高亮框。
     * 归一化坐标（0~1）转换为像素坐标。
     */
    private void drawHighlightBox(Canvas canvas,
                                  FrameAnalysisResult.BoundingBox box,
                                  int viewW, int viewH) {
        float left = box.x * viewW;
        float top = box.y * viewH;
        float right = (box.x + box.width) * viewW;
        float bottom = (box.y + box.height) * viewH;

        RectF rect = new RectF(left, top, right, bottom);

        // 半透明填充
        canvas.drawRect(rect, highlightFillPaint);
        // 虚线描边
        canvas.drawRect(rect, highlightPaint);

        // 文字标签（优先放在框上方，超出顶部则放框下方）
        if (box.label != null && !box.label.isEmpty()) {
            float textY = top - 8f;
            if (textY < 30f) {
                textY = bottom + 40f;
            }
            canvas.drawText(box.label, left + 8f, textY, textPaint);
        }
    }

    /**
     * 绘制全屏警告。
     * 顶部红色横幅 + 红色闪烁边框。
     */
    private void drawWarning(Canvas canvas, int w, int h) {
        // 顶部红色横幅
        float bannerHeight = h / 6f;
        canvas.drawRect(0f, 0f, (float) w, bannerHeight, warningBgPaint);

        // 警告文字
        String displayText = "\u26A0 " + warningText;
        canvas.drawText(displayText, w / 2f, h / 10f, warningTextPaint);

        // 闪烁效果：红色边框 alpha 动态变化
        int alpha = (int) (128 + 127 * Math.sin(System.currentTimeMillis() / 300.0));
        warningBgPaint.setAlpha(alpha);
        canvas.drawRect(0f, 0f, (float) w, (float) h, warningBgPaint);

        // 恢复原始 alpha
        warningBgPaint.setAlpha(0xCC);
    }

    /** 绘制底部偏上的文字提示卡片。 */
    private void drawGuideCard(Canvas canvas, int w, int h) {
        float margin = 32f;
        float cardHeight = 110f;
        float left = margin;
        float top = h - cardHeight - 36f;
        float right = w - margin;
        float bottom = h - 36f;
        RectF rect = new RectF(left, top, right, bottom);

        canvas.drawRoundRect(rect, 18f, 18f, guideBgPaint);

        textPaint.setTextSize(34f);
        String text = guideText.length() > 22 ? guideText.substring(0, 22) + "..." : guideText;
        canvas.drawText(text, left + 24f, top + 66f, textPaint);
    }
}
