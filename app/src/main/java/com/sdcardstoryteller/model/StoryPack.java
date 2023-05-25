/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.sdcardstoryteller.model;

import java.util.List;

public class StoryPack {

    public static final StoryPack EMPTY = new StoryPack();

    private String uuid;
    private boolean factoryDisabled;
    private short version;
    private List<StageNode> stageNodes;
    private boolean nightModeAvailable = false;

    public StoryPack() {
    }

    public StoryPack(String uuid, boolean factoryDisabled, short version, List<StageNode> stageNodes, boolean nightModeAvailable) {
        this.uuid = uuid;
        this.factoryDisabled = factoryDisabled;
        this.version = version;
        this.stageNodes = stageNodes;
        this.nightModeAvailable = nightModeAvailable;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public boolean isFactoryDisabled() {
        return factoryDisabled;
    }

    public void setFactoryDisabled(boolean factoryDisabled) {
        this.factoryDisabled = factoryDisabled;
    }

    public short getVersion() {
        return version;
    }

    public void setVersion(short version) {
        this.version = version;
    }

    public List<StageNode> getStageNodes() {
        return stageNodes;
    }

    public void setStageNodes(List<StageNode> stageNodes) {
        this.stageNodes = stageNodes;
    }

    public boolean isNightModeAvailable() {
        return nightModeAvailable;
    }

    public void setNightModeAvailable(boolean nightModeAvailable) {
        this.nightModeAvailable = nightModeAvailable;
    }
}
