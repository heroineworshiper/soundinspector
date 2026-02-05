/*
 * SoundInspector
 * Copyright (C) 2026 Adam Williams <broadcast at earthling dot net>
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 * 
 */

// read an audio file in the background & compute the FFT

package com.soundinspector;


import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.Semaphore;

class FileReader extends Thread {
    Semaphore started = new Semaphore(0);
    Semaphore finished = new Semaphore(0);
    boolean done = false;
    boolean busy = false;
    boolean interrupted = false;
    String currentDir = "";
    String currentFile = "";
    static FileReader instance = null;
    // all threads access this to determine the start of the next job
    int processed = 0;
    int rowspan = 0;
    int cpus = 1;

    class FFTThread extends Thread
    {
        boolean isMane = false; // this one updates the GUI

        public void run()
        {
            // get next job
            float[] real_in = new float[Stuff.WINDOW_SIZE];

            int FRAGMENT_SIZE = Stuff.bitmapW / (cpus * 4) + 1;
            while(true) {
                int fragment = FRAGMENT_SIZE;
                int start = 0;
                synchronized (FileReader.instance) {
                    if (FileReader.instance.processed >= Stuff.bitmapW ||
                            FileReader.instance.interrupted) break;
                    start = FileReader.instance.processed;
                    if(FileReader.instance.processed + fragment > Stuff.bitmapW)
                        fragment = Stuff.bitmapW - FileReader.instance.processed;
                    FileReader.instance.processed += fragment;
                }

                float max_real = 0;
                Timer timer = new Timer();
                for (int i = start; !FileReader.instance.interrupted && i < start + fragment; i++)
                {
                    int offset1 = i * Stuff.WINDOW_SIZE * Stuff.channels * 2;

//                        Log.i("FileReader", "run " + offset1 + " " + Settings.pcm[offset1]);
// convert 1 window of samples
                    for (int j = 0; j < Stuff.WINDOW_SIZE; j++) {
// average all the channels
                        int sample = 0;
                        for (int k = 0; k < Stuff.channels; k++) {
                            int offset2 = offset1 + j * Stuff.channels * 2 + k * 2;
                            int sample2 = (Stuff.pcm[offset2] & 0xff) +
                                    (Stuff.pcm[offset2 + 1] << 8); // extend sign
                            sample += sample2;
                        }
                        real_in[j] = (float) sample / Stuff.channels / 32768;
                        if (Math.abs(real_in[j]) > max_real) max_real = Math.abs(real_in[j]);
                        //(j == Settings.WINDOW_SIZE -1) Log.i("FileReader", "run i=" + i + " real_in=" + real_in[j]);

                    }
                    float[] real_out = new float[Stuff.WINDOW_SIZE];
                    float[] imag_out = new float[Stuff.WINDOW_SIZE];
                    FFT.doFFT(real_in,
                            real_out,
                            imag_out,
                            Stuff.WINDOW_SIZE);

                    float max = 0;
                    for (int j = 0; j < Stuff.WINDOW_SIZE / 2; j++) {
                        float mag = (float) Math.hypot(real_out[j], imag_out[j]);
                        //if(j == 0) Log.i("FileReader", "run i=" + i + " mag=" + mag);
                        real_out[j] = mag;
                        if (mag > max) max = mag;
                    }
// normalize
                    if (max > 0 && max_real > 0)
                        for (int j = 0; j < Stuff.WINDOW_SIZE / 2; j++) {
                            real_out[j] *= max_real / max;
                        }


// convert to bitmap columns
// scale rows to log frequency in the bitmap height & log magnitude
                    for (int row = 0; row < Stuff.BITMAP_H; row++) {
                        float value = 0;
                        // interpolate
                        float row_f = PlayerWin.freq_scale((float) row / Stuff.BITMAP_H) * (Stuff.WINDOW_SIZE / 2);
                        float weight2 = (float)(row_f - (int)row_f);
                        float weight1 = (float)(1.0 - weight2);
                        int row1_i = (int)row_f;
                        int row2_i = (int)(row1_i + 1);
                        if(row2_i >= Stuff.WINDOW_SIZE / 2) row2_i = Stuff.WINDOW_SIZE / 2 - 1;
                        value = PlayerWin.mag_scale(real_out[row1_i] * weight1 + real_out[row2_i] * weight2);

                        int flip_row = Stuff.BITMAP_H - row - 1;
                        to_color(Stuff.waveformBuffer, rowspan * flip_row + i * 4, value);
                    }

                    if (isMane && timer.getDiff() > 250) {
                        timer.reset();
                        PlayerWin.instance.drawSpectrum();
                    }
                }

            }
        }
    }

    FileReader() {
        start();
    }

    public void submitFile() {

        if (Stuff.pcm == null ||
                !currentDir.equals(Stuff.currentDir) ||
                !currentFile.equals(Stuff.currentFile))
        {
            Log.i("FileReader", "submitFile: new file " + Stuff.currentDir + "/" + Stuff.currentFile);
            Log.i("FileReader", "submitFile: old file " + currentDir + "/" + currentFile);

            if (busy) {
                interrupted = true;
                try { finished.acquire(); } catch (Exception e) {}
                busy = false;
            }

            interrupted = false;
            busy = true;
            currentDir = Stuff.currentDir;
            currentFile = Stuff.currentFile;
            Stuff.instance.reset(true, false);
            Log.i("FileReader", "submitFile 2");
            started.release();
            Log.i("FileReader", "submitFile 3");
        } else {
            Log.i("FileReader", "submitFile no change");
        }
    }

    public void run() {
        while (!done) {
            try {
                started.acquire(); // wait for command
                if(done) break;

                Timer timer = new Timer();
                PlayerWin.instance.drawMessage("DECODED 0%");

                File file = new File(currentDir + "/" + currentFile);
                long fileLength = file.length();
                long totalRead = 0;
                MediaExtractor extractor = new MediaExtractor();
                extractor.setDataSource(file.getAbsolutePath());
                Log.i("FileReader", "run tracks=" + extractor.getTrackCount());
                for (int i = 0; !interrupted && i < extractor.getTrackCount(); i++) {
                    MediaFormat mf = extractor.getTrackFormat(i);
                    String mime = mf.getString(MediaFormat.KEY_MIME);
                    Log.i("FileReader", "run mime=" + mime);
                    if (mime != null && mime.startsWith("audio/")) {
                        extractor.selectTrack(i);
                        MediaCodec decoder = MediaCodec.createDecoderByType(mime);
                        decoder.configure(mf, null, null, 0);
                        decoder.start();

                        boolean gotInputEOF = false;
                        boolean gotOutputEOF = false;

                        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                        ByteArrayOutputStream pcmOutput = new ByteArrayOutputStream(1024 * 1024); // growable
                        while (!gotOutputEOF && !interrupted) {
                            // Feed input
                            if (!gotInputEOF) {
                                int inputBufIndex = decoder.dequeueInputBuffer(1000000);
                                if (inputBufIndex >= 0) {
                                    ByteBuffer inputBuf = decoder.getInputBuffer(inputBufIndex);
                                    int chunkSize = extractor.readSampleData(inputBuf, 0);

                                    if (chunkSize < 0) {
                                        // End of stream
                                        decoder.queueInputBuffer(inputBufIndex, 0, 0, 0,
                                                MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                        gotInputEOF = true;
                                    } else {
                                        long presentationTimeUs = extractor.getSampleTime();
                                        int flags = extractor.getSampleFlags();

                                        decoder.queueInputBuffer(inputBufIndex, 0, chunkSize,
                                                presentationTimeUs, flags);
                                        extractor.advance();
                                        totalRead += chunkSize;
                                        if (timer.getDiff() > 100) {
                                            timer.reset();
                                            String s = "DECODED " + String.valueOf(totalRead * 100 / fileLength) + "%";
                                            PlayerWin.instance.drawMessage(s);


//                                            Log.i("FileReader", "run 1 " + s);
                                        }
                                    }
                                }
                            }

                            // Get output
                            int outputBufIndex = decoder.dequeueOutputBuffer(info, 1000000);
                            //Log.i("FileReader", "run: outputBufIndex=" + outputBufIndex + " info.size=" + info.size);

                            if (outputBufIndex >= 0) {
                                ByteBuffer outputBuf = decoder.getOutputBuffer(outputBufIndex);

                                if (info.flags == MediaCodec.BUFFER_FLAG_CODEC_CONFIG) {
                                    // Usually empty for audio decoders \u2192 ignore
                                } else if (info.size > 0) {
                                    // -------------------------------
                                    //  Here is where we get the PCM
                                    // -------------------------------
                                    byte[] chunk = new byte[info.size];
                                    outputBuf.position(info.offset);
                                    outputBuf.get(chunk);               // <--- copy PCM bytes
                                    outputBuf.position(info.offset);    // restore (good practice)
                                    pcmOutput.write(chunk);
// Total length is unknown, so can't draw the waveform here
                                }

                                decoder.releaseOutputBuffer(outputBufIndex, false);

                                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                    gotOutputEOF = true;
                                }
                            } else if (outputBufIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                                // New format \u2014 usually happens once
                                MediaFormat newFormat = decoder.getOutputFormat();
                                Log.i("FileReader", "run: Output format changed: " + newFormat);
                                Stuff.samplerate = newFormat.getInteger("sample-rate");
                                Stuff.channels = newFormat.getInteger("channel-count");
                            } else if (outputBufIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                                // timeout \u2192 continue
                            }
                        }

                        decoder.stop();
                        decoder.release();
                        extractor.release();

                        Stuff.pcm = pcmOutput.toByteArray();
                        Log.i("FileReader", "run: got " + Stuff.pcm.length + " bytes");
                        break;
                    }
                }


                Log.i("FileReader", "run done decoding pcm=" + Stuff.pcm + " waveformH=" + Stuff.waveformH);

// create spectrogram & draw it
                if (!interrupted && Stuff.pcm != null && Stuff.waveformH > 0) {
                    //PlayerWin.instance.drawMessage("FFT 0%");
                    Stuff.length = Stuff.pcm.length /
                            Stuff.channels /
                            2;
                    Stuff.bitmapW = Stuff.length / Stuff.WINDOW_SIZE;
                    if(Stuff.zoomX < 0) {
                        Stuff.zoomX = (float) Stuff.bitmapW / Stuff.waveformW;
                        Stuff.zoomY = (float) Stuff.BITMAP_H / Stuff.waveformH;
                    }
                    Log.i("FileReader", "run done decoding windows=" + Stuff.bitmapW + " zoom=" + Stuff.zoomX);

                    // test bitmap size
                    // TODO: use tiling or increase the window spacing
                    if (Stuff.bitmapW > Stuff.maxBitmapW) {
                        String s = "FILE TOO LARGE\n" +
                                String.valueOf(Stuff.bitmapW) + " WINDOWS\n" +
                                String.valueOf(Stuff.maxBitmapW) + " MAXIMUM";
                        PlayerWin.instance.drawMessage(s);
                        Stuff.instance.reset(true, false);
                        finished.release();
                        continue;
                    }

                    // TODO: get this from the bitmap
                    rowspan = Stuff.bitmapW * 4;
                    Stuff.waveformBuffer = new byte[Stuff.BITMAP_H * rowspan];
                    Arrays.fill(Stuff.waveformBuffer, (byte) 255);
                    cpus = Runtime.getRuntime().availableProcessors();
                    processed = 0;
                    FFTThread[]  fft_threads = new FFTThread[cpus];
                    for(int i = 0; i < cpus; i++) {
                        fft_threads[i] = new FFTThread();
                        if(i == 0) fft_threads[i].isMane = true;
                        fft_threads[i].start();
                    }
                    for(int i = 0; i < cpus; i++)
                        fft_threads[i].join();


                    PlayerWin.instance.drawSpectrum();
                    Log.i("FileReader", "run done FFT");
                }

                finished.release();
            } catch (Exception e) {
                Log.e("FileReader", "run " + e.toString());
                finished.release();
            }
        }
    }

    void hsv_to_rgb(float[] rgb, float h, float s, float v)
    {
        int i;
        float min, max, delta;
        float f, p, q, t;
        if(s == 0)
        {
            // achromatic (grey)
            rgb[0] = rgb[1] = rgb[2] = v;
            return;
        }

        h /= 60;                        // sector 0 to 5
        i = (int)h;
        f = h - i;                      // factorial part of h
        p = v * (1 - s);
        q = v * (1 - s * f);
        t = v * (1 - s * (1 - f));

        switch(i)
        {
            case 0:
                rgb[0] = v;
                rgb[1] = t;
                rgb[2] = p;
                break;
            case 1:
                rgb[0] = q;
                rgb[1] = v;
                rgb[2] = p;
                break;
            case 2:
                rgb[0] = p;
                rgb[1] = v;
                rgb[2] = t;
                break;
            case 3:
                rgb[0] = p;
                rgb[1] = q;
                rgb[2] = v;
                break;
            case 4:
                rgb[0] = t;
                rgb[1] = p;
                rgb[2] = v;
                break;
            default:                // case 5:
                rgb[0] = v;
                rgb[1] = p;
                rgb[2] = q;
                break;
        }
    }

    void to_color(byte[] dst, int offset, float value)
    {
        //float division = (float)0.5;
        float division = (float)1;
        byte r = 0;
        byte g = 0;
        byte b = 0;
        if(value >= division)
        {
            float h = (float)(240 - (value - division) / (1.0 - division) * 240);
            if(h < 0) h = 0;
            if(h > 240) h = 240;

            float[] rgb = new float[3];
            hsv_to_rgb(rgb, h, 1, 1);
            r = (byte)(rgb[0] * 255);
            g = (byte)(rgb[1] * 255);
            b = (byte)(rgb[2] * 255);
        }
        else
        {
            r = g = b = (byte)(value / division * 255);
        }
        dst[offset + 0] = r;
        dst[offset + 1] = g;
        dst[offset + 2] = b;
        dst[offset + 3] = (byte) (255);

    }
}





