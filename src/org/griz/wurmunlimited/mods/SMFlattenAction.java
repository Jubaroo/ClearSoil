package org.griz.wurmunlimited.mods;

import com.wurmonline.mesh.MeshIO;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.server.*;
import com.wurmonline.server.behaviours.*;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.*;
import com.wurmonline.server.players.Player;
import com.wurmonline.server.skills.NoSuchSkillException;
import com.wurmonline.server.skills.Skill;
import com.wurmonline.server.skills.Skills;
import com.wurmonline.server.sounds.SoundPlayer;
import com.wurmonline.server.structures.BridgePart;
import com.wurmonline.server.structures.Fence;
import com.wurmonline.server.utils.logging.TileEvent;
import com.wurmonline.server.zones.NoSuchZoneException;
import com.wurmonline.server.zones.VolaTile;
import com.wurmonline.server.zones.Zone;
import com.wurmonline.server.zones.Zones;
import org.gotti.wurmunlimited.modsupport.actions.ActionPerformer;
import org.gotti.wurmunlimited.modsupport.actions.BehaviourProvider;
import org.gotti.wurmunlimited.modsupport.actions.ModAction;
import org.gotti.wurmunlimited.modsupport.actions.ModActions;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by John on 12/8/2016.  Updated 5/26/2018
 *
 * This action will surface mine a rock tile down by one dirt level on all four corners if possible.
 * Still WIP,  should be limited to GM's and above.
 */
public class SMFlattenAction implements ModAction{

    VolaTile til;

    private Logger logger = Logger.getLogger( SMFlattenAction.class.getName());
    private final short actionId;
    private final ActionEntry actionEntry;

    private int Skill;

    public SMFlattenAction(int skill){
        actionId = (short) ModActions.getNextActionId();
        Skill = skill;
        actionEntry = ActionEntry.createEntry( actionId, "Lower Tile", "lowering a tile", new int[] {6,36,43,48});
        ModActions.registerAction( actionEntry);
    }

    @Override
    public BehaviourProvider getBehaviourProvider() {
        return new BehaviourProvider() {

            @Override
            public List<ActionEntry> getBehavioursFor(Creature performer, Item object, int tilex, int tiley, boolean onSurface, int tile) {

                if( performer.getPower() < 2){
                    //any player less than GM status will not see this action.
                    return null;
                }

                Skill digging;
                Skills skills = performer.getSkills();

                try {
                    digging = skills.getSkill(1008);
                } catch (Exception e) {
                    digging = skills.learn(1008, 0.0F);
                }

                if( digging.knowledge < Skill ){
                    return null;
                }

                if( performer instanceof Player && object.isMiningtool() && Tiles.decodeType( tile) == Tiles.Tile.TILE_ROCK.id && performer.getFloorLevel() == 0 && performer.isWithinTileDistanceTo(tilex, tiley, 0, 0)){
                    return Collections.singletonList(actionEntry);
                } else {
                    return null;
                }
            }
        };
    }

    @Override
    public ActionPerformer getActionPerformer(){
        return new ActionPerformer() {

            @Override
            public short getActionId() {
                return actionId;
            }

            @Override
            public boolean action(Action action, Creature performer, Item source, int tilex, int tiley, boolean onSurface, int heightOffset, int tile, short num, float counter) {
                try {
                    if (counter == 1.0f) {
                        if( !performer.isWithinTileDistanceTo( tilex, tiley, 0, 0)){
                            performer.getCommunicator().sendNormalServerMessage("You must stand on the tile you want to lower.");
                            return true;
                        }

                        if (!performer.isOnSurface()) {
                            performer.getCommunicator().sendNormalServerMessage("You can not mine from the inside.");
                            return true;
                        }

                        if (Tiles.decodeType(tile) != Tiles.Tile.TILE_ROCK.id) {
                            performer.getCommunicator().sendNormalServerMessage("You can not mine this tile down");
                            return true;
                        }

                        //is the tile protected?
                        if (Zones.protectedTiles[tilex][tiley]) {
                            performer.getCommunicator().sendNormalServerMessage("Your body goes limp and you find no strength to continue here. Weird.");
                            return true;
                        }

                        performer.getCommunicator().sendNormalServerMessage("You start to lower the stone");

                        String var37 = "sound.work.mining1";
                        int x = Server.rand.nextInt(3);
                        if (x == 0) {
                            var37 = "sound.work.mining2";
                        } else if (x == 1) {
                            var37 = "sound.work.mining3";
                        }
                        SoundPlayer.playSound(var37, performer, 0.0F);

                        int time = 50;
                        performer.getCurrentAction().setTimeLeft(time);
                        performer.sendActionControl("Mining", true, time);

                    } else {
                        int time = performer.getCurrentAction().getTimeLeft();;

                        if (counter * 10 > time) {
                            FlattenRockTile(action, (short) 145, tilex, tiley, tile, performer, source, counter);
                            FlattenRockTile(action, (short) 145, tilex + 1, tiley, tile, performer, source, counter);
                            FlattenRockTile(action, (short) 145, tilex, tiley + 1, tile, performer, source, counter);
                            FlattenRockTile(action, (short) 145, tilex + 1, tiley + 1, tile, performer, source, counter);

                            performer.getCommunicator().sendNormalServerMessage("You finish mining.");
                            return true;
                        }
                    }
                    return false;
                } catch (NoSuchActionException e) {
                    performer.getCommunicator().sendNormalServerMessage("Error while lowering tile.");
                    return true;
                }
            }
        };
    }

    //FlattenRockTile is a misleading name, should refactor this!
    private boolean FlattenRockTile( Action act, short action, int tilex, int tiley, int tile, Creature performer, Item source, float counter){
        int it;
        int resource;
        int itemTemplate;
        //boolean done;
        Skill miningskill;
        Random rockRandom = new Random();
        boolean onSurface = performer.isOnSurface();

        if(source.isMiningtool() && action == 145) {
            int var63 = tilex;
            int var65 = tiley;
            //int var63 = (int)performer.getStatus().getPositionX() + 2 >> 2;
            //int var65 = (int)performer.getStatus().getPositionY() + 2 >> 2;
            //int tile = Server.surfaceMesh.getTile(var63, var65);

            if(var63 < 1 || var63 > (1 << Constants.meshSize) - 1 || var65 < 1 || var65 > (1 << Constants.meshSize) - 1) {
                performer.getCommunicator().sendNormalServerMessage("The water is too deep to mine.", (byte)3);
                return true;
            }

            if(Zones.isTileProtected(var63, var65)) {
                performer.getCommunicator().sendNormalServerMessage("This tile is protected by the gods. You can not mine here.", (byte)3);
                return true;
            }

            short var67 = Tiles.decodeHeight(tile);
            if(var67 > -25) {
                //done = false;
                Skills var69 = performer.getSkills();
                //String findString = null;
                Skill var71 = null;

                try {
                    miningskill = var69.getSkill(1008);
                } catch (Exception var55) {
                    miningskill = var69.learn(1008, 1.0F);
                }

                try {
                    var71 = var69.getSkill(source.getPrimarySkill());
                } catch (Exception var54) {
                    try {
                        var71 = var69.learn(source.getPrimarySkill(), 1.0F);
                    } catch (NoSuchSkillException var53) {
                        logger.log(Level.WARNING, performer.getName() + " trying to mine with an item with no primary skill: " + source.getName());
                    }
                }

                int var74;
                int var72;
                for(var72 = -1; var72 <= 0; ++var72) {
                    for(var74 = -1; var74 <= 0; ++var74) {
                        byte var77 = Tiles.decodeType(Server.surfaceMesh.getTile(var63 + var72, var65 + var74));
                        if(var77 != Tiles.Tile.TILE_ROCK.id && var77 != Tiles.Tile.TILE_CLIFF.id) {
                            performer.getCommunicator().sendNormalServerMessage("The surrounding area needs to be rock before you mine.", (byte)3);
                            return true;
                        }
                    }
                }

                VolaTile var78;
                for(var72 = 0; var72 >= -1; --var72) {
                    for(var74 = 0; var74 >= -1; --var74) {
                        var78 = Zones.getTileOrNull(var63 + var72, var65 + var74, onSurface);
                        if(var78 != null && var78.getStructure() != null) {
                            if(var78.getStructure().isTypeHouse()) {
                                if(var72 == 0 && var74 == 0) {
                                    performer.getCommunicator().sendNormalServerMessage("You cannot mine in a building.", (byte)3);
                                } else {
                                    performer.getCommunicator().sendNormalServerMessage("You cannot mine next to a building.", (byte)3);
                                }

                                return true;
                            }

                            BridgePart[] var80 = var78.getBridgeParts();
                            resource = var80.length;

                            for(itemTemplate = 0; itemTemplate < resource; ++itemTemplate) {
                                BridgePart var86 = var80[itemTemplate];
                                if(var86.getType().isSupportType()) {
                                    if(var72 == 0 && var74 == 0) {
                                        performer.getCommunicator().sendNormalServerMessage("You cannot mine in a bridge support.", (byte)3);
                                    } else {
                                        performer.getCommunicator().sendNormalServerMessage("You cannot mine next to a bridge support.", (byte)3);
                                    }

                                    return true;
                                }
                            }
                        }
                    }
                }

                VolaTile var75 = Zones.getTileOrNull(var63, var65, onSurface);
                if(var75 != null && var75.getFencesForLevel(0).length > 0) {
                    performer.getCommunicator().sendNormalServerMessage("You cannot mine next to a fence.", (byte)3);
                    return true;
                }

                var75 = Zones.getTileOrNull(var63, var65 - 1, onSurface);
                Fence[] var76;
                Fence var81;
                if(var75 != null && var75.getFencesForLevel(0).length > 0) {
                    var76 = var75.getFencesForLevel(0);
                    int x = var76.length;

                    for(it = 0; it < x; ++it) {
                        var81 = var76[it];
                        if(!var81.isHorizontal()) {
                            performer.getCommunicator().sendNormalServerMessage("You cannot mine next to a fence.", (byte)3);
                            return true;
                        }
                    }
                }

                var75 = Zones.getTileOrNull(var63 - 1, var65, onSurface);
                if(var75 != null && var75.getFencesForLevel(0).length > 0) {
                    var76 = var75.getFencesForLevel(0);
                    int x = var76.length;

                    for(it = 0; it < x; ++it) {
                        var81 = var76[it];
                        if(var81.isHorizontal()) {
                            performer.getCommunicator().sendNormalServerMessage("You cannot mine next to a fence.", (byte)3);
                            return true;
                        }
                    }
                }

                //var74 = 0;
                var78 = Zones.getTileOrNull((int)performer.getPosX() >> 2, (int)performer.getPosY() >> 2, onSurface);
                if(var78 != null && var78.getNumberOfItems(performer.getFloorLevel()) > 99) {
                    performer.getCommunicator().sendNormalServerMessage("There is no space to mine here. Clear the area first.", (byte)3);
                    return true;
                }

//                try {
//                    var74 = performer.getCurrentAction().getTimeLeft();
//                } catch (NoSuchActionException var51) {
//                    logger.log(Level.INFO, "This action does not exist?", var51);
//                }

                if(act.getRarity() != 0) {
                    performer.playPersonalSound("sound.fx.drumroll");
                }

                //it = Terraforming.getMaxSurfaceDifference(Server.surfaceMesh.getTile(var63, var65), var63, var65);
//                        if((double)it > miningskill.getKnowledge(0.0D) * (double)(Servers.localServer.PVPSERVER?1:3)) {
//                            performer.getCommunicator().sendNormalServerMessage("You are too unskilled to mine here.", (byte)3);
//                            return true;
//                        }

                double var85 = 0.0D;
                double var88 = 0.0D;
                //done = true;
                boolean var91 = true;
                float diff = 1.0F;
                int caveTile = Server.caveMesh.getTile(var63, var65);
                short caveFloor = Tiles.decodeHeight(caveTile);
                //int caveCeilingHeight = CaveTile.decodeCeilingHeight( caveTile);
                int caveCeilingHeight = caveFloor + (short)(Tiles.decodeData(caveTile) & 255);
                MeshIO mesh = Server.surfaceMesh;
                if(var67 - 1 <= caveCeilingHeight) {
                    performer.getCommunicator().sendNormalServerMessage("The rock sounds hollow. You need to tunnel to proceed.",(byte) 3);
                    return true;
                }

                double imbueEnhancement = 1.0D + 0.23047D * (double)source.getSkillSpellImprovement(1008) / 100.0D;
                short maxDiff = (short)((int)Math.max(10.0D, miningskill.getKnowledge(0.0D) * 3.0D * imbueEnhancement));

                if(Terraforming.isAltarBlocking(performer, tilex, tiley)) {
                    performer.getCommunicator().sendSafeServerMessage("You cannot build here, since this is holy ground.", (byte)2);
                    return true;
                }

                if(performer.getTutorialLevel() == 10 && !performer.skippedTutorial()) {
                    performer.missionFinished(true, true);
                }

                if(var71 != null) {
                    var85 = var71.skillCheck(1.0D, source, 0.0D, false, counter) / 5.0D;
                }

                var88 = Math.max(1.0D, miningskill.skillCheck(1.0D, source, var85, false, counter));

                try {
                    if(miningskill.getKnowledge(0.0D) * imbueEnhancement < var88) {
                        var88 = miningskill.getKnowledge(0.0D) * imbueEnhancement;
                    }

                    rockRandom.setSeed((long)(var63 + var65 * Zones.worldTileSizeY) * 789221L);
                    //boolean ex = true;
                    int max1 = Math.min(100, (int)(20.0D + (double)rockRandom.nextInt(80) * imbueEnhancement));
                    var88 = Math.min(var88, (double)max1);
                    if(source.isCrude()) {
                        var88 = 1.0D;
                    }

                    float orePower = GeneralUtilities.calcOreRareQuality(var88, act.getRarity(), source.getRarity());


                    Item newItem = ItemFactory.createItem(146, orePower, performer.getPosX(), performer.getPosY(), Server.rand.nextFloat() * 360.0F, performer.isOnSurface(), act.getRarity(), -10L, (String) null);
                    newItem.setLastOwnerId(performer.getWurmId());
                    newItem.setDataXY(tilex, tiley);
                    performer.getCommunicator().sendNormalServerMessage("You mine some " + newItem.getName() + ".");
                    Server.getInstance().broadCastAction(performer.getName() + " mines some " + newItem.getName() + ".", performer, 5);
                    TileEvent.log(tilex, tiley, 0, performer.getWurmId(), action);
                    int tTile = mesh.getTile( tilex, tiley);
                    short newHeight = (short) (Tiles.decodeHeight( tTile) - 1);
                    mesh.setTile(tilex, tiley, Tiles.encode(newHeight, Tiles.Tile.TILE_ROCK.id, Tiles.decodeData(tile)));
                    Server.rockMesh.setTile(tilex, tiley, Tiles.encode(newHeight, (short) 0));

                    for (int xx = 0; xx >= -1; --xx) {
                        for (int yy = 0; yy >= -1; --yy) {
                            performer.getMovementScheme().touchFreeMoveCounter();
                            Players.getInstance().sendChangedTile(var63 + xx, var65 + yy, performer.isOnSurface(), true);

                            try {
                                Zone nsz = Zones.getZone(var63 + xx, var65 + yy, performer.isOnSurface());
                                nsz.changeTile(var63 + xx, var65 + yy);
                            } catch (NoSuchZoneException var50) {
                                logger.log(Level.INFO, "no such zone?: " + tilex + ", " + tiley, var50);
                            }
                        }
                    }


                } catch (Exception var62) {
                    logger.log(Level.WARNING, "Factory failed to produce item", var62);
                }
            } else {
                performer.getCommunicator().sendNormalServerMessage("The water is too deep to mine.", (byte)3);
            }
        }
        return false;
    }

    /*
    static final int getItemTemplateForTile(byte type) {
        return type == Tiles.Tile.TILE_CAVE_WALL_ORE_COPPER.id?43:(type == Tiles.Tile.TILE_CAVE_WALL_ORE_GOLD.id?39:(type == Tiles.Tile.TILE_CAVE_WALL_ORE_IRON.id?38:(type == Tiles.Tile.TILE_CAVE_WALL_ORE_LEAD.id?41:(type == Tiles.Tile.TILE_CAVE_WALL_ORE_SILVER.id?40:(type == Tiles.Tile.TILE_CAVE_WALL_ORE_TIN.id?207:(type == Tiles.Tile.TILE_CAVE_WALL_ORE_ZINC.id?42:(type == Tiles.Tile.TILE_CAVE_WALL_ORE_ADAMANTINE.id?693:(type == Tiles.Tile.TILE_CAVE_WALL_ORE_GLIMMERSTEEL.id?697:(type == Tiles.Tile.TILE_CAVE_WALL_MARBLE.id?785:(type == Tiles.Tile.TILE_CAVE_WALL_SLATE.id?770:146))))))))));
    }

    static String getShardQlDescription(int shardQl) {
        return shardQl < 10?"really poor quality":(shardQl < 30?"poor quality":(shardQl < 40?"acceptable quality":(shardQl < 60?"normal quality":(shardQl < 80?"good quality":(shardQl < 95?"very good quality":"utmost quality")))));
    }
    */
}
