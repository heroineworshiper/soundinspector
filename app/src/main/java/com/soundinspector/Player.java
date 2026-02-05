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



package com.soundinspector;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

import java.util.concurrent.Semaphore;

public class Player extends Thread {
    static Player instance = null;
    Semaphore started = new Semaphore(0);
    Semaphore finished = new Semaphore(0);
    volatile boolean done;
    boolean busy; // must acquire finished if this is true
    volatile boolean interrupted = false;

    Player() {
        start();
    }

    void play()
    {
        if (busy) {
            interrupted = true;
            try { finished.acquire(); } catch (Exception e) {}
            busy = false;
        }

        if(!Stuff.paused)
            Stuff.playCurrent = Stuff.playStart;
        if(Stuff.pcm != null && Stuff.playCurrent < Stuff.bitmapW) {
            interrupted = false;
            busy = true;
            Stuff.playing = true;
            Stuff.paused = false;
            started.release();
        }
    }

    void pause()
    {
        if(busy)
        {
            interrupted = true;
            try { finished.acquire(); } catch (Exception e) {}
            busy = false;
        }
        Stuff.playing = false;
        Stuff.paused = true;
    }

    void stopPlayback()
    {
        if(busy)
        {
            Log.i("Player", "stopPlayback");
            interrupted = true;
            try { finished.acquire(); } catch (Exception e) {}
            busy = false;
        }
        Stuff.playing = false;
        Stuff.paused = false;
    }



    public void run() {
        while (!done)
        {
            // wait for command
            try { started.acquire(); } catch (Exception e) {}
            if(done) break;
            int bufsize = 4096; // must be smaller than length
            int channels;
            int playStart = Stuff.playCurrent;
            int offset = playStart * Stuff.WINDOW_SIZE * Stuff.channels * 2;
            int total = Stuff.length * Stuff.channels * 2;

// move view to start of playback
            synchronized (PlayerWin.instance)
            {
                float playCurrentX = playStart / Stuff.zoomX;
                float relX = playCurrentX - Stuff.viewX;
                if(relX > Stuff.waveformW * 3 / 4 || relX < 0)
                {
                    Stuff.viewX = playCurrentX - Stuff.waveformW / 2;
                    Stuff.viewX = Stuff.clamp(Stuff.viewX,0, Stuff.bitmapW / Stuff.zoomX - Stuff.waveformW);
                }
            }

            Timer timer = new Timer();
            if(Stuff.channels == 1)
                    channels = AudioFormat.CHANNEL_CONFIGURATION_MONO;
            else
                    channels = AudioFormat.CHANNEL_CONFIGURATION_STEREO;
            AudioTrack track = new AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    Stuff.samplerate,
                    channels,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufsize,
                    AudioTrack.MODE_STREAM);
            track.play();
            while(!interrupted && offset < total)
            {
                int fragment = bufsize;
                if(offset + fragment > total) fragment = total - offset;
                track.write(Stuff.pcm, offset, fragment);
                offset += fragment;

// update cursor
                Stuff.playCurrent = playStart + track.getPlaybackHeadPosition() / Stuff.WINDOW_SIZE;
                //Log.i("Player", "run playCurrent=" + Stuff.playCurrent);


                if(timer.getDiff() > 1000 / 60)
                {
                    timer.reset();
// view follows playback
                    synchronized (PlayerWin.instance)
                    {
                        float playCurrentX = Stuff.playCurrent / Stuff.zoomX;
                        float relX = playCurrentX - Stuff.viewX;
                        if(relX > Stuff.waveformW * 3 / 4 && relX < Stuff.waveformW && !Stuff.is_dragging)
                        {
                            Stuff.viewX = playCurrentX - Stuff.waveformW * 3 / 4;
                            Stuff.viewX = Stuff.clamp(Stuff.viewX,0, Stuff.bitmapW / Stuff.zoomX - Stuff.waveformW);
                        }
// scroll left when we restart playback.  Doesn't work so well if the user manually scrolls right during playback.
//                        else
//                        if(relX < 0 && !Stuff.is_dragging)
//                        {
//                            Stuff.viewX = playCurrentX - Stuff.waveformW * 1 / 4;
//                            Stuff.viewX = Stuff.clamp(Stuff.viewX,0, Stuff.bitmapW / Stuff.zoomX - Stuff.waveformW);
//                        }
                    }
                    PlayerWin.instance.drawSpectrum();
                }
            }
            
// hit EOF
            if(!interrupted)
            {
                Stuff.playing = false;
                Stuff.paused = false;
                PlayerWin.instance.updateButton();
                PlayerWin.instance.drawSpectrum();
            }
            Log.i("Player", "run done");


            finished.release();
        }

    }

}
