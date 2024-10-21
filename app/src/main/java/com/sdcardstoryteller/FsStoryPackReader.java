package com.sdcardstoryteller;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static com.sdcardstoryteller.XXTEACipher.readCipheredFile;
import com.sdcardstoryteller.model.ActionNode;
import com.sdcardstoryteller.model.AudioAsset;
import com.sdcardstoryteller.model.ControlSettings;
import com.sdcardstoryteller.model.ImageAsset;
import com.sdcardstoryteller.model.StageNode;
import com.sdcardstoryteller.model.StoryPack;
import com.sdcardstoryteller.model.Transition;
import com.sdcardstoryteller.model.metadata.StoryPackMetadata;

public class FsStoryPackReader {

    private static final String NODE_INDEX_FILENAME = "ni";
    private static final String LIST_INDEX_FILENAME = "li";
    private static final String IMAGE_INDEX_FILENAME = "ri";
    private static final String IMAGE_FOLDER = "rf" + File.separator;
    private static final String SOUND_INDEX_FILENAME = "si";
    private static final String SOUND_FOLDER = "sf" + File.separator;
    private static final String NIGHT_MODE_FILENAME = "nm";
    private static final String CLEARTEXT_FILENAME = ".cleartext";
    private static final byte[] CLEARTEXT_RI_BEGINNING = "000\\".getBytes(StandardCharsets.UTF_8);

    private byte[] readFile(Path file, boolean isCleartext) throws IOException {
        if (isCleartext) {
            return Files.readAllBytes(file);
        } else {
            return readCipheredFile(file);
        }
    }

    private byte[] readNBytes(DataInputStream dis, int len) throws IOException {
        // Custom implementation of DataInputStream.readNBytes to be compatible with API version
        byte[] out = new byte[len];
        int n = dis.read(out);
        if (n<len) {
            if (n<1) {
                return new byte[0];
            }
            return Arrays.copyOfRange(out,0,n);
        }
        return out;
    }

    public StoryPackMetadata readMetadata(Path inputFolder) throws IOException {
        // Pack metadata model
        StoryPackMetadata metadata = new StoryPackMetadata(Constants.PACK_FORMAT_FS);

        // Open 'ni' file
        File packFolder = inputFolder.toFile();
        FileInputStream niFis = new FileInputStream(new File(packFolder, NODE_INDEX_FILENAME));
        DataInputStream niDis = new DataInputStream(niFis);
        ByteBuffer bb = ByteBuffer.wrap(readNBytes(niDis,512)).order(ByteOrder.LITTLE_ENDIAN);
        metadata.setVersion(bb.getShort(2));
        niDis.close();
        niFis.close();

        // Folder name is the uuid (minus the eventual timestamp, so we just trim everything starting at the dot)
        String uuid = inputFolder.getFileName().toString().split("\\.", 2)[0];
        metadata.setUuid(uuid);

        // Night mode is available if file 'nm' exists
        metadata.setNightModeAvailable(new File(packFolder, NIGHT_MODE_FILENAME).exists());

        return metadata;
    }

    public StoryPack read(File packFolder) throws IOException {
        TreeMap<Integer, StageNode> stageNodes = new TreeMap<>();                   // Keep stage nodes
        TreeMap<Integer, Integer> actionNodesOptionsCount = new TreeMap<>();        // Keep action nodes' options count
        TreeMap<Integer, List<Transition>> transitionsWithAction = new TreeMap<>(); // Transitions must be updated with the actual ActionNode

        // Folder name is the uuid (minus the eventual timestamp, so we just trim everything starting at the dot)
        String uuid = packFolder.getName();// inputFolder.getFileName().toString().split("\\.", 2)[0];

        // Night mode is available if file 'nm' exists
        boolean nightModeAvailable = new File(packFolder, NIGHT_MODE_FILENAME).exists();

        // Assets are cleartext if file '.cleartext' exists
        boolean isCleartext = isCleartext(packFolder, false);

        // Load ri, si and li files
        //System.out.println("Reading riContent");
        byte[] riContent = readFile(new File(packFolder, IMAGE_INDEX_FILENAME).toPath(), isCleartext);
        //System.out.println("Reading siContent");
        byte[] siContent = readFile(new File(packFolder, SOUND_INDEX_FILENAME).toPath(), isCleartext);
        //System.out.println("Reading liContent");
        byte[] liContent = readFile(new File(packFolder, LIST_INDEX_FILENAME).toPath(), isCleartext);

        // Open 'ni' file
        FileInputStream niFis = new FileInputStream(new File(packFolder, NODE_INDEX_FILENAME));
        DataInputStream niDis = new DataInputStream(niFis);
        ByteBuffer bb = ByteBuffer.wrap(readNBytes(niDis,512)).order(ByteOrder.LITTLE_ENDIAN);
        // Nodes index file format version (1)
        bb.getShort();
        // Story pack version
        short version = bb.getShort();
        // Start of actual nodes list in this file (0x200 / 512)
        int nodesList = bb.getInt();
        // Size of a stage node in this file (0x2C / 44)
        int nodeSize = bb.getInt();
        // Number of stage nodes in this file
        int stageNodesCount = bb.getInt();
        // Number of images (in RI file and rf/ folder)
        int imageAssetsCount = bb.getInt();
        // Number of sounds (in SI file and sf/ folder)
        int soundAssetsCount = bb.getInt();
        // Is factory pack (boolean) set to true to avoid pack inspection by official Luniistore application
        boolean factoryDisabled = bb.get() != 0x00;

        // Read stage nodes
        for (int i=0; i<stageNodesCount; i++) {
            bb = ByteBuffer.wrap(readNBytes(niDis,nodeSize)).order(ByteOrder.LITTLE_ENDIAN);
            int imageAssetIndexInRI = bb.getInt();
            int soundAssetIndexInSI = bb.getInt();
            int okTransitionActionNodeIndexInLI = bb.getInt();
            int okTransitionNumberOfOptions = bb.getInt();
            int okTransitionSelectedOptionIndex = bb.getInt();
            int homeTransitionActionNodeIndexInLI = bb.getInt();
            int homeTransitionNumberOfOptions = bb.getInt();
            int homeTransitionSelectedOptionIndex = bb.getInt();
            boolean wheel = bb.getShort() != 0;
            boolean ok = bb.getShort() != 0;
            boolean home = bb.getShort() != 0;
            boolean pause = bb.getShort() != 0;
            boolean autoplay = bb.getShort() != 0;

            // Transition will be updated later with the actual action nodes
            Transition okTransition = null;
            if (okTransitionActionNodeIndexInLI != -1 && okTransitionNumberOfOptions != -1 && okTransitionSelectedOptionIndex != -1) {
                if (!actionNodesOptionsCount.containsKey(okTransitionActionNodeIndexInLI)) {
                    actionNodesOptionsCount.put(okTransitionActionNodeIndexInLI, okTransitionNumberOfOptions);
                }
                okTransition = new Transition(null, (short) okTransitionSelectedOptionIndex);
                List<Transition> twa = transitionsWithAction.getOrDefault(okTransitionActionNodeIndexInLI, new ArrayList<>());
                twa.add(okTransition);
                transitionsWithAction.put(okTransitionActionNodeIndexInLI, twa);
            }
            Transition homeTransition = null;
            if (homeTransitionActionNodeIndexInLI != -1 && homeTransitionNumberOfOptions != -1 && homeTransitionSelectedOptionIndex != -1) {
                if (!actionNodesOptionsCount.containsKey(homeTransitionActionNodeIndexInLI)) {
                    actionNodesOptionsCount.put(homeTransitionActionNodeIndexInLI, homeTransitionNumberOfOptions);
                }
                homeTransition = new Transition(null, (short) homeTransitionSelectedOptionIndex);
                List<Transition> twa = transitionsWithAction.getOrDefault(homeTransitionActionNodeIndexInLI, new ArrayList<>());
                twa.add(homeTransition);
                transitionsWithAction.put(homeTransitionActionNodeIndexInLI, twa);
            }

            // Read Image and audio assets
            ImageAsset image = null;
            if (imageAssetIndexInRI != -1) {
                // Read image path
                byte[] imagePath = Arrays.copyOfRange(riContent, imageAssetIndexInRI*12, imageAssetIndexInRI*12+12);   // Each entry takes 12 bytes
                String path = new String(imagePath, StandardCharsets.UTF_8);
                // Read image file
                byte[] rfContent = readFile(new File(packFolder, IMAGE_FOLDER+path.replaceAll("\\\\", "/")).toPath(), isCleartext);
                image = new ImageAsset("image/bmp", rfContent);
            }
            AudioAsset audio = null;
            if (soundAssetIndexInSI != -1) {
                // Read audio path
                byte[] audioPath = Arrays.copyOfRange(siContent, soundAssetIndexInSI*12, soundAssetIndexInSI*12+12);    // Each entry takes 12 bytes
                String path = new String(audioPath, StandardCharsets.UTF_8);
                // Read audio file
                Path f = new File(packFolder, SOUND_FOLDER+path.replaceAll("\\\\", "/")).toPath();
                audio = new AudioAsset("audio/mpeg", f);
            }

            StageNode stageNode = new StageNode(
                    i == 0 ? uuid : UUID.randomUUID().toString(), // First node should have the same UUID as the story pack FIXME node uuids from metadata file
                    image,
                    audio,
                    okTransition,
                    homeTransition,
                    new ControlSettings(
                            wheel,
                            ok,
                            home,
                            pause,
                            autoplay
                    )
            );
            stageNodes.put(i, stageNode);
        }

        niDis.close();
        niFis.close();

        // Read action nodes from 'li' file
        ByteBuffer liBb = ByteBuffer.wrap(liContent).order(ByteOrder.LITTLE_ENDIAN);
        for (Map.Entry<Integer, Integer> actionCount: actionNodesOptionsCount.entrySet()) {
            Integer offset = actionCount.getKey();
            Integer count = actionCount.getValue();
            List<StageNode> options = new ArrayList<>(count);
            liBb.position(offset*4);    // Each entry takes 4 bytes
            for (int i=0; i<count; i++) {
                int stageNodeIndex = liBb.getInt();
                options.add(stageNodes.get(stageNodeIndex));
            }
            // Update action on transitions referencing this sector
            ActionNode actionNode = new ActionNode(options);
            transitionsWithAction.get(offset).forEach(transition -> transition.setActionNode(actionNode));
        }

        return new StoryPack(uuid, factoryDisabled, version, List.copyOf(stageNodes.values()), nightModeAvailable);
    }

    public boolean isCleartext(File packFolder, boolean fixBrokenCleartext) throws IOException {

        // Assets are cleartext if file '.cleartext' exists
        boolean isCleartext = new File(packFolder, CLEARTEXT_FILENAME).exists();

        if (fixBrokenCleartext) {
            // Fix broken story packs with missing .cleartext file
            byte[] riRawContent = Files.readAllBytes(new File(packFolder, IMAGE_INDEX_FILENAME).toPath());
            if (!isCleartext && Arrays.equals(riRawContent, 0, CLEARTEXT_RI_BEGINNING.length, CLEARTEXT_RI_BEGINNING, 0, CLEARTEXT_RI_BEGINNING.length)) {
                //System.out.println("Story pack contains cleartext data but is missing .cleartext file: fixing...");

                // Indicate that files are cleartext
                new File(packFolder, CLEARTEXT_FILENAME).createNewFile();

                isCleartext = true;
            }
        }

        return isCleartext;
    }
}
