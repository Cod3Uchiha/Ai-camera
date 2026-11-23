package com.cod3uchiha.aicamera

import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Qualcomm ESRGAN float32 TFLite runner.
 * Expected input [1,128,128,3], output [1,512,512,3], RGB values in [0,1].
 * Tiles the input and downsamples the model's 4x output to a memory-conscious 2x result.
 */
class LiteSrEngine(modelFile: File) {
    private val interpreter: Interpreter

    init {
        val modelBytes = modelFile.readBytes()
        val modelBuffer = ByteBuffer.allocateDirect(modelBytes.size).order(ByteOrder.nativeOrder())
        modelBuffer.put(modelBytes)
        modelBuffer.rewind()
        interpreter = Interpreter(modelBuffer, Interpreter.Options().setNumThreads(4))
        require(interpreter.getInputTensor(0).shape().contentEquals(intArrayOf(1, 128, 128, 3))) {
            "Model input must be [1,128,128,3]"
        }
        require(interpreter.getOutputTensor(0).shape().contentEquals(intArrayOf(1, 512, 512, 3))) {
            "Model output must be [1,512,512,3]"
        }
    }

    fun enhance(source: Bitmap): Bitmap {
        val maxPixels = 24_000_000L
        val scale = minOf(2f, kotlin.math.sqrt(maxPixels.toDouble() / (source.width.toLong() * source.height)).toFloat())
        val outWidth = (source.width * scale).toInt().coerceAtLeast(1)
        val outHeight = (source.height * scale).toInt().coerceAtLeast(1)
        val output = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        val input = ByteBuffer.allocateDirect(128 * 128 * 3 * 4).order(ByteOrder.nativeOrder())
        val modelOutput = ByteBuffer.allocateDirect(512 * 512 * 3 * 4).order(ByteOrder.nativeOrder())
        val tilePixels = IntArray(128 * 128)

        var top = 0
        while (top < source.height) {
            var left = 0
            while (left < source.width) {
                for (ty in 0 until 128) {
                    for (tx in 0 until 128) {
                        val sx = (left + tx).coerceAtMost(source.width - 1)
                        val sy = (top + ty).coerceAtMost(source.height - 1)
                        tilePixels[ty * 128 + tx] = source.getPixel(sx, sy)
                    }
                }
                input.clear()
                for (pixel in tilePixels) {
                    input.putFloat(((pixel shr 16) and 0xFF) / 255f)
                    input.putFloat(((pixel shr 8) and 0xFF) / 255f)
                    input.putFloat((pixel and 0xFF) / 255f)
                }
                input.rewind()
                modelOutput.clear()
                interpreter.run(input, modelOutput)
                modelOutput.rewind()

                val validW = minOf(128, source.width - left)
                val validH = minOf(128, source.height - top)
                val destX = (left * scale).toInt()
                val destY = (top * scale).toInt()
                val copyW = minOf((validW * scale).toInt(), outWidth - destX)
                val copyH = minOf((validH * scale).toInt(), outHeight - destY)
                val pixels = IntArray(copyW * copyH)
                for (oy in 0 until copyH) {
                    for (ox in 0 until copyW) {
                        val mx = ((ox / scale) * 4f).toInt().coerceIn(0, 511)
                        val my = ((oy / scale) * 4f).toInt().coerceIn(0, 511)
                        val index = ((my * 512 + mx) * 3) * 4
                        val r = readFloat(modelOutput, index).coerceIn(0f, 1f)
                        val g = readFloat(modelOutput, index + 4).coerceIn(0f, 1f)
                        val b = readFloat(modelOutput, index + 8).coerceIn(0f, 1f)
                        pixels[oy * copyW + ox] = (0xFF shl 24) or
                            ((r * 255).toInt() shl 16) or ((g * 255).toInt() shl 8) or (b * 255).toInt()
                    }
                }
                output.setPixels(pixels, 0, copyW, destX, destY, copyW, copyH)
                left += 128
            }
            top += 128
        }
        return output
    }

    private fun readFloat(buffer: ByteBuffer, byteIndex: Int): Float = buffer.getFloat(byteIndex)

    fun close() = interpreter.close()
}
