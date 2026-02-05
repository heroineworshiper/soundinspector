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

import android.content.Context;
import android.content.SharedPreferences;

public class Stuff {
	static Stuff instance;


	// the file being viewed
	static String currentDir = FileSelect.DEFAULT_DIR;
	static String currentFile = "Untitled";

	// the audio data
	static byte pcm[] = null;
	// FFT windows
	static final int WINDOW_SIZE = 4096;
	// bitmap height
	static final int BITMAP_H = 1024;
	// section of the canvas devoted to the waveform
	static int waveformW = -1;
	static int waveformH = -1;
	static int waveformY;
	// the total canvas size
	static int canvasW = -1;
	static int canvasH = -1;
	// maximum bitmap size
	static int maxBitmapW = -1;
	static int bitmapW; // number of FFT windows or bitmap width
	static byte[] waveformBuffer; // the big spectrum bitmap of the entire file
	static float zoomX = -1;  // bitmap columns per pixel.  fit to window if -1
	static float zoomY = -1; // bitmap rows per pixel.   fit to window if -1
	static float viewX = 0; // starting column of view in waveformW
	static float viewY = 0; // starting row of view in waveformH
	static float velocity_x; // last velocity in screen pixels per ms
	static float velocity_y;
	static Timer velocity_timer = new Timer(); // time since last velocity update
	static boolean is_dragging;

	static int length; // sound length in samples
	static int samplerate;
	static int channels;

	static boolean playing = false;
	static boolean paused = false;
	static int playStart; // start of playback in bitmap columns
	static int playCurrent; // current position if playing back

	Stuff(Context context)
	{
		reset(true, true);
		load(context);
	}

	static void init(Context context)
	{
		if(instance == null) instance = new Stuff(context);
		if(FileReader.instance == null) FileReader.instance = new FileReader();
		if(Player.instance == null) Player.instance = new Player();
	}

	static void load(Context context)
	{
		SharedPreferences file = context.getSharedPreferences("settings", 0);
		FileSelect.selectedDir = file.getString("selectedDir", FileSelect.selectedDir);
		FileSelect.selectedFile = file.getString("selectedFile", FileSelect.selectedFile);

		currentFile = file.getString("currentFile", currentFile);
		currentDir = file.getString("currentDir", currentDir);
		playStart = file.getInt("playStart", playStart);
		viewX = file.getFloat("viewX", viewX);
		viewY = file.getFloat("viewY", viewY);
		zoomX = file.getFloat("zoomX", zoomX);
		zoomY = file.getFloat("zoomY", zoomY);
	}
	
	
	static void save(Context context)
	{
		SharedPreferences file2 = context.getSharedPreferences("settings", 0);
		SharedPreferences.Editor file = file2.edit();

		file.putString("selectedDir", FileSelect.selectedDir);
		file.putString("selectedFile", FileSelect.selectedFile);
		file.putString("currentDir", currentDir);
		file.putString("currentFile", currentFile);
		file.putInt("playStart", playStart);
		file.putFloat("viewX", viewX);
		file.putFloat("viewY", viewY);
		file.putFloat("zoomX", zoomX);
		file.putFloat("zoomY", zoomY);

		file.commit();
	}

	void reset(boolean buffers, boolean view)
	{
		synchronized (this) {
			if(buffers) {
				pcm = null;
				waveformBuffer = null;
			}
			if(view) {
				zoomX = -1;
				zoomY = -1;
				viewX = 0;
				viewY = 0;
				playStart = 0;
			}
			playCurrent = 0;
		}
	}

	static float clamp(float x, float min, float max)
	{
		if(x > max) x = max;
		if(x < min) x = min;
		return x;
	}
}






