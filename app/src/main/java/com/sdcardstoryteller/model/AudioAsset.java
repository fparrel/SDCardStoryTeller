/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.sdcardstoryteller.model;

import java.nio.file.Path;

public class AudioAsset implements Asset {

    private String mimeType;

    private Path path;

    public AudioAsset() {
    }

    public AudioAsset(String mimeType, Path path) {
        this.mimeType = mimeType;
        this.path = path;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public Path getPath() {
        return path;
    }

    public void setPath(Path path) {
        path = path;
    }

}
