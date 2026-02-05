/*
 * Ultramap
 * Copyright (C) 2021 Adam Williams <broadcast at earthling dot net>
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

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Vector;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.app.Activity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class FileSelect extends Activity {

    class DirEntry {

        String path;
        String name;
        boolean isDir;
        long size;
        long date;


        Vector<DirEntry> contents = new Vector<DirEntry>();
        DirEntry parent;


        DirEntry(String path) {
            this.path = path;
            int i = path.lastIndexOf('/');
            if (i >= 0) {
                name = path.substring(i + 1);
            } else {
                name = path;
            }
            File file = new File(path);
            isDir = file.isDirectory();
            size = file.length();
            date = file.lastModified();
        }
    }

    class CustomAdapter extends ArrayAdapter<String> {

        Context context;
        DirEntry[] files;

        public CustomAdapter(Context context, DirEntry[] files) {
            super(context,
                    android.R.layout.simple_list_item_1,
                    android.R.id.text1);
            this.context = context;
            this.files = files;
        }

        public void setHighlightPosition(int position) {
            notifyDataSetChanged();   // very important!
        }

        @Override
        public int getCount() {
            return files.length;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;

            if (row == null) {
                LayoutInflater inflater = (LayoutInflater) context
                        .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                row = inflater.inflate(android.R.layout.simple_list_item_1, parent, false);
            }

            TextView textView = row.findViewById(android.R.id.text1);
            //Log.i("CustomAdapter", "textView=" + textView + " position=" + position);
            if(textView != null) {
                DirEntry file = files[position];
                if (file.isDir)
                {
                    textView.setText(file.name +"/");
                    textView.setTextColor(Color.BLUE);
                }
                else
                {
                    textView.setText(file.name);
                }
            }

            return row;
        }
    }

    class SortByName implements Comparator<DirEntry>
    {
        // Used for sorting in ascending order of
        // roll number
        public int compare(DirEntry a, DirEntry b) {
            int result = a.name.toLowerCase().compareTo(b.name.toLowerCase());
            if (!sortDescending) {
                return result;
            } else
            {
                return -result;
            }
        }
    }

    class SortBySize implements Comparator<DirEntry>
    {
        // Used for sorting in ascending order of
        // roll number
        public int compare(DirEntry a, DirEntry b)
        {
            if(sortDescending)
            {
                if(a.size < b.size)
                {
                    return 1;
                }
                else
                {
                    return -1;
                }
            }
            else
            {
                if(b.size < a.size)
                {
                    return 1;
                }
                else
                {
                    return -1;
                }
            }
        }
    }


    class SortByDate implements Comparator<DirEntry>
    {
        // Used for sorting in ascending order of
        // roll number
        public int compare(DirEntry a, DirEntry b)
        {
            if(sortDescending)
            {
                if(a.date < b.date)
                {
                    return 1;
                }
                else
                {
                    return -1;
                }
            }
            else
            {
                if(b.date < a.date)
                {
                    return 1;
                }
                else
                {
                    return -1;
                }
            }
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Stuff.init(this);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.file_select);
        success = false;
        update();
    }


    void update()
    {

        File dir = new File(selectedDir);
        File[] mFileList = dir.listFiles();

        if(mFileList == null)
        {
            selectedDir = DEFAULT_DIR;
            dir = new File(selectedDir);
            mFileList = dir.listFiles();
            Log.i("FileSelect", "update denied selectedDir=" + selectedDir);
        }

        if(mFileList != null)
        {
            DirEntry[] files = new DirEntry[mFileList.length];
            for(int i = 0; i < mFileList.length; i++) {
                //Log.i("FileSelect", "file=" + mFileList[i].getAbsolutePath());
                files[i] = new DirEntry(mFileList[i].getAbsolutePath());
            }

            switch (sortOrder) {
                case SORT_SIZE:
                    Arrays.sort(files, new SortBySize());
                    break;
                case SORT_DATE:
                    Arrays.sort(files, new SortByDate());
                    break;
                default:
                case SORT_PATH:
                    Arrays.sort(files, new SortByName());
                    break;
            }

// http://www.vogella.com/articles/AndroidListView/article.html
            ListView listView = (ListView) findViewById(R.id.listView1);

//        Log.v("FileSelect", "onCreate 2 " +
//        		this + " " +
//        		Environment.getRootDirectory() + " " +
//        		Environment.getDataDirectory() + " " +
//        		mPath + " " +
//        		mFileList);
            CustomAdapter adapter = new CustomAdapter(this, files);
            listView.setAdapter(adapter);
            listView.setOnItemClickListener(new ListView.OnItemClickListener()
            {
                public void onItemClick(AdapterView<?> parent,
                                        View view,
                                        int position,
                                        long id)
                {
                    TextView title = (TextView)findViewById(R.id.file_text);
                    DirEntry file = files[position];
                    if(file.isDir) {
                        // set the textbox
//                        title.setText(file.name + "/");
                        // go into the directory
                        selectedDir += "/" + file.name;
                        // strip trailing /
                        while(selectedDir.length() > 0 && selectedDir.charAt(selectedDir.length() - 1) == '/')
                            selectedDir = selectedDir.substring(0, selectedDir.length() - 1);
                        selectedFile = "";
                        update();

                    }
                    else
                        title.setText(files[position].name);
                }
            });

        }



        Log.i("FileSelect", "update selectedDir=" + selectedDir + " selectedFile=" + selectedFile);
        TextView text = (TextView) findViewById(R.id.currentDir);
        if(text != null) text.setText(selectedDir + "/");

        if(FileSelect.selectedFile != null)
        {
            TextView title = (TextView)findViewById(R.id.file_text);
            title.setText(FileSelect.selectedFile);
        }

    }



    public void onClick(View view)
    {
    	TextView title = (TextView)findViewById(R.id.file_text);
    	selectedFile = title.getText().toString();
        boolean done = false;

        switch (view.getId()) {
            case R.id.file_up: {
                int lastIndex = selectedDir.lastIndexOf('/');
                if(lastIndex >= 0) {
                    selectedDir = selectedDir.substring(0, lastIndex);
                    if(selectedDir.length() == 0) selectedDir = "/";
                    selectedFile = "";
                    Log.i("FileSelect", "selectedDir=" + selectedDir);
                    update();
                }
                break;
            }
	        case R.id.file_ok:
	        {
//        	    Log.v("FileSelect", "onClick 1 " + FileSelect.selectedFile);
                String testPath = selectedDir + "/" + selectedFile;
                File testFile = new File(testPath);

                if(testFile.isDirectory()) {
                    selectedDir += "/" + selectedFile;
                    // strip trailing /
                    while(selectedDir.length() > 0 && selectedDir.charAt(selectedDir.length() - 1) == '/')
                        selectedDir = selectedDir.substring(0, selectedDir.length() - 1);
                    selectedFile = "";
                    update();
                }
                else {
                    success = true;
                    done = true;
                }
	        }
	        break;
	        
	        case R.id.file_cancel:
	        {
        	    Log.v("FileSelect", "onClick 2");
        	    success = false;
                done = true;
	        }
	        break;
        }

        // go back to previous activity
        if(done) finish();

        // load the nextWindow
//         Intent i = new Intent(this, nextWindow);
//         i.setFlags(i.getFlags() | Intent.FLAG_ACTIVITY_NO_HISTORY);
//         startActivity(i);
    }
    

    
    
// http://stackoverflow.com/questions/3592717/choose-file-dialog
	static boolean success = false;
// next window after closing
    static Class nextWindow = null;
    static final int SORT_PATH = 0;
    static final int SORT_SIZE = 1;
    static final int SORT_DATE = 2;
    static int sortOrder = SORT_PATH;
    static boolean sortDescending = false;
    static final String DEFAULT_DIR = "/sdcard";
    static String selectedDir = DEFAULT_DIR;
    static String selectedFile = "";
}
