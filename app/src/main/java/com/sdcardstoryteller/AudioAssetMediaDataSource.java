/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.sdcardstoryteller;

import android.media.MediaDataSource;

import com.sdcardstoryteller.model.AudioAsset;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.Arrays;

public class AudioAssetMediaDataSource extends MediaDataSource {

    private long size = -1;

    private long current_pos = 0;

    private byte[] decryptedBlock = null;

    private final AudioAsset asset;

    private FileInputStream fis = null;

    public AudioAssetMediaDataSource(AudioAsset asset) {
        this.asset = asset;
    }

    @Override
    public int readAt(long position, byte[] buffer, int offset, int size) throws IOException {
        //System.out.println("readAt "+position+" "+offset+" "+size);
        if (fis == null) {
            fis = new FileInputStream(asset.getPath().toString());
        }
        if (position < current_pos) {
            //System.out.println(" position="+position+"  current_pos="+current_pos);
            fis = new FileInputStream(asset.getPath().toString());
            if (position < 512) {
                current_pos = 0;
            } else {
                fis.skip(position);
                current_pos = position;
            }
        } else if(position > current_pos) {
            fis.skip(position - current_pos);
            current_pos = position;
        }
        if (position < 512) {
            // In case we ask for data in first block, we decrypt first block, then return it
            // caller may have asked for more than 512 bytes, but in this case we just return 512 bytes or less
            // which doesn't seems to be an issue
            if (decryptedBlock == null) {
                byte[] block = new byte[512];
                int n = fis.read(block, 0, 512);
                if (n > 0) {
                    current_pos += n;
                    int[] dataInt = XXTEACipher.toIntArray(Arrays.copyOfRange(block, 0, n), ByteOrder.LITTLE_ENDIAN);
                    int[] decryptedInt = XXTEACipher.btea(dataInt, -(Math.min(128, n / 4)), XXTEACipher.toIntArray(XXTEACipher.COMMON_KEY, ByteOrder.BIG_ENDIAN));
                    decryptedBlock = XXTEACipher.toByteArray(decryptedInt, ByteOrder.LITTLE_ENDIAN);
                }
            }
            buffer = Arrays.copyOfRange(decryptedBlock, (int) position,Math.min(size,decryptedBlock.length));
            //System.out.println("r:"+buffer.length);
            return buffer.length;
        } else {
            int n = fis.read(buffer, offset, size);
            //System.out.println("r:" + n);
            current_pos += n;
            return n;
        }
    }

    @Override
    public long getSize() throws IOException {
        if (size==-1) {
            // unencrypted block has same size as encrypted block
            size = new File(asset.getPath().toString()).length();
        }
        //System.out.println("getSize "+size);
        return size;
    }

    @Override
    public void close() throws IOException {
        fis.close();
    }
}
