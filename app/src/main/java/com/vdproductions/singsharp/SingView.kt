package com.vdproductions.singsharp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

class SingView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private val screenWidth = resources.displayMetrics.widthPixels;
    private val screenHeight = resources.displayMetrics.heightPixels;
    private val largerDimension = screenWidth.coerceAtLeast(screenHeight);
    private val scaleFactor = resources.displayMetrics.density * largerDimension / 1000;
    private val sideNotes = 3
    private var note: Triple<Int, Int, Float> = Triple(0, 3, 0f)
    private val textSize: Float = 35 * scaleFactor
    private val fillColor: Int = Color.GREEN
    private val strokeColor: Int = Color.BLACK
    private val noteNames = arrayOf("C", "C", "D", "D", "E", "F", "F", "G", "G", "A", "A", "B")
    private val noteSigns = arrayOf("", "#", "", "#", "", "", "#", "", "#", "", "#", "")

    fun setViewModel(viewModel: SingViewModel) {
        // Observe LiveData
        viewModel.note.observeForever { newNote ->
            note = newNote
            invalidate()
        }
    }

    val fillPaint = Paint().apply {
        color = fillColor
        textSize = this@SingView.textSize
        textAlign = Paint.Align.CENTER
        strokeWidth = this@SingView.textSize / 40
    }

    val strokePaint = Paint().apply {
        color = strokeColor
        textSize = fillPaint.textSize
        textAlign = fillPaint.textAlign
        strokeWidth = fillPaint.textSize / 30
        style = Paint.Style.STROKE
    }

    val subAndSuperScriptFillPaint = Paint().apply {
        color = fillColor
        textSize = this@SingView.textSize / 2
        textAlign = Paint.Align.CENTER
    }

    val subAndSuperScriptStrokePaint = Paint().apply {
        color = strokeColor
        textSize = subAndSuperScriptFillPaint.textSize
        textAlign = subAndSuperScriptFillPaint.textAlign
        strokeWidth = strokePaint.strokeWidth
        style = Paint.Style.STROKE
    }

    fun getCenterX(): Float {
        return width / 2f
    }

    fun getCenterY(): Float {
        return (height / 2f - (strokePaint.descent() + strokePaint.ascent()) / 2)
    }

    val notesDistance = strokePaint.textSize * 2
    var lineOffsetY = strokePaint.textSize * 0.1f;
    val lineHeight = strokePaint.textSize / 4
    val arrowWidth = strokePaint.textSize / 16

    fun createArrowPath(): Path {
        val x = getCenterX()
        val y = getCenterY()
        val arrowPath = Path()
        arrowPath.moveTo(x, y + lineOffsetY + lineHeight / 2)
        arrowPath.lineTo(x - arrowWidth, y + lineHeight * 1.5f + lineOffsetY)
        arrowPath.lineTo(x + arrowWidth, y + lineHeight * 1.5f + lineOffsetY)
        arrowPath.close()
        return arrowPath
    }

    fun setGradient(paint: Paint) {
        paint.shader = LinearGradient(
            0f, 0f, width.toFloat(), 0f,
            intArrayOf(Color.TRANSPARENT, paint.color, paint.color, Color.TRANSPARENT),
            floatArrayOf(0f, 0.25f, 0.75f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Calculate text position
        val x = getCenterX()
        val y = getCenterY()

        note.let { (noteIndex, octave, centsOffset) ->
            val noteOffset = (notesDistance * centsOffset) / -100f

            for (indexOffset in -sideNotes..sideNotes) {
                val currentNoteIndex = (noteIndex + indexOffset + 12) % 12
                var currentOctave = if (noteIndex + indexOffset < 0) octave - 1 else if (noteIndex + indexOffset > 11) octave + 1 else octave
                drawNote(canvas, currentNoteIndex, currentOctave, x + notesDistance * indexOffset + noteOffset, y)
            }

            val arrowPath = createArrowPath()
            canvas.drawPath(arrowPath, strokePaint)
            canvas.drawPath(arrowPath, fillPaint)
        }
    }

    fun drawNote(canvas: Canvas, noteIndex: Int, octave: Int, x: Float, y: Float) {
        drawOutlinedText(canvas, noteNames[noteIndex], x, y, fillPaint, strokePaint)
        drawOutlinedText(canvas, noteSigns[noteIndex], x + strokePaint.textSize * 0.5f, y - strokePaint.textSize * 0.4f, subAndSuperScriptFillPaint, subAndSuperScriptStrokePaint)
        drawOutlinedText(canvas, octave.toString(), x + strokePaint.textSize * 0.5f, y + strokePaint.textSize * 0.1f, subAndSuperScriptFillPaint, subAndSuperScriptStrokePaint)

        drawOutlineLine(canvas, x, y + lineOffsetY, x, y + lineOffsetY + lineHeight, fillPaint, strokePaint)

        for(lineIndex in 1..9)
        {
            val xOffset = notesDistance / 10 * lineIndex
            drawOutlineLine(canvas, x + xOffset, y + lineOffsetY + lineHeight / 2, x + xOffset, y + lineOffsetY + lineHeight, fillPaint, strokePaint)
        }
    }

    fun drawOutlinedText(canvas: Canvas, text: String, x: Float, y: Float, fillPaint: Paint, strokePaint: Paint) {
        setGradient(strokePaint)
        canvas.drawText(text, x, y, strokePaint)
        setGradient(fillPaint)
        canvas.drawText(text, x, y, fillPaint)
    }

    fun drawOutlineLine(canvas: Canvas, startX: Float, startY: Float, endX: Float, endY: Float, fillPaint: Paint, strokePaint: Paint) {
        setGradient(strokePaint)
        canvas.drawLine(startX, startY, endX, endY, strokePaint)
        setGradient(fillPaint)
        canvas.drawLine(startX, startY, endX, endY, fillPaint)
    }
}
