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




class FFT
{
    static public int samples_to_bits(int samples)
    {
        int i = 0;

        for(i = 0; ; i++)
        {
            if((samples & (1 << i)) == (1 << i))
                return i;
        }
    }

    static public int reverse_bits(int index, int bits)
    {
        int i, rev;

        for(i = rev = 0; i < bits; i++)
        {
            rev = (rev << 1) | (index & 1);
            index >>= 1;
        }

        return rev;
    }

    static public void doFFT(float[] real_in,
        float[] real_out,
        float[] imag_out,
        int samples)
    {
        int num_bits = samples_to_bits(samples);
        
        for(int i = 0; i < samples; i++)
        {
            int j = reverse_bits(i, num_bits);
            real_out[j] = real_in[i];
            imag_out[j] = 0;
        }
        
        int block_end = 1;
        float angle_numerator = (float)(2.0 * Math.PI);
        float[] ar = new float[3];
        float[] ai = new float[3];
        for(int block_size = 2; block_size <= samples; block_size <<= 1)
        {
            float delta_angle = angle_numerator / (float)block_size;
            float sm2 = (float)Math.sin(-2 * delta_angle);
            float sm1 = (float)Math.sin(-delta_angle);
            float cm2 = (float)Math.cos(-2 * delta_angle);
            float cm1 = (float)Math.cos(-delta_angle);
            float w = 2 * cm1;

            for(int i = 0; i < samples; i += block_size)
            {
                ar[2] = cm2;
                ar[1] = cm1;

                ai[2] = sm2;
                ai[1] = sm1;

                for(int j = i, n = 0; n < block_end; j++, n++)
                {
                    ar[0] = w * ar[1] - ar[2];
                    ar[2] = ar[1];
                    ar[1] = ar[0];

                    ai[0] = w * ai[1] - ai[2];
                    ai[2] = ai[1];
                    ai[1] = ai[0];

                    int k = j + block_end;
                    float tr = ar[0] * real_out[k] - ai[0] * imag_out[k];
                    float ti = ar[0] * imag_out[k] + ai[0] * real_out[k];

                    real_out[k] = real_out[j] - tr;
                    imag_out[k] = imag_out[j] - ti;

                    real_out[j] += tr;
                    imag_out[j] += ti;
                }
            }

            block_end = block_size;
        }
    }
};


