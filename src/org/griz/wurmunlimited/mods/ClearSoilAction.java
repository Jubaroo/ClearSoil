package org.griz.wurmunlimited.mods;

/**
 * Created by John on 10/6/2016.   Updated 5/15/2018
 */

import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;

import com.wurmonline.mesh.MeshIO;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.server.*;
import com.wurmonline.server.behaviours.*;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.endgames.EndGameItem;
import com.wurmonline.server.endgames.EndGameItems;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemFactory;
import com.wurmonline.server.items.NoSuchTemplateException;
import com.wurmonline.server.players.Player;

import com.wurmonline.server.skills.NoSuchSkillException;
import com.wurmonline.server.skills.Skill;
import com.wurmonline.server.skills.Skills;
import com.wurmonline.server.sounds.SoundPlayer;
import com.wurmonline.server.structures.BridgePart;
import com.wurmonline.server.structures.Fence;
import com.wurmonline.server.utils.logging.TileEvent;
import com.wurmonline.server.villages.Village;
import com.wurmonline.server.zones.NoSuchZoneException;
import com.wurmonline.server.zones.VolaTile;
import com.wurmonline.server.zones.Zone;
import com.wurmonline.server.zones.Zones;
import org.gotti.wurmunlimited.modsupport.actions.ActionPerformer;
import org.gotti.wurmunlimited.modsupport.actions.BehaviourProvider;
import org.gotti.wurmunlimited.modsupport.actions.ModAction;
import org.gotti.wurmunlimited.modsupport.actions.ModActions;


public class ClearSoilAction implements ModAction{

    private static Logger logger = Logger.getLogger( ClearSoilAction.class.getName());

    private final short actionId;
    private final ActionEntry actionEntry;

    private int Skill;

    public ClearSoilAction( int MinSkill){
        Skill = MinSkill;
        actionId = (short) ModActions.getNextActionId();
        actionEntry = ActionEntry.createEntry( actionId, "Clear Soil", "clearing", new int[] {6,36,43,48});
        ModActions.registerAction( actionEntry);
    }

    @Override
    public BehaviourProvider getBehaviourProvider(){
        return new BehaviourProvider() {

            @Override
            public List<ActionEntry> getBehavioursFor(Creature performer, Item object, int tilex, int tiley, boolean onSurface, int tile){
                Skills skills = performer.getSkills();
                Skill digging;

                try {
                    digging = skills.getSkill(1009);
                } catch (Exception e) {
                    digging = skills.learn(1009, 0.0F);
                }

                //if( digging.knowledge < Skill ){
                if( digging.getRealKnowledge() < Skill ){
                    return null;
                }

                if( tile != performer.getCurrentTileNum()){
                    return null;
                }

                if( Tiles.decodeType( tile) == Tiles.Tile.TILE_PEAT.id || Tiles.decodeType( tile) == Tiles.Tile.TILE_CLAY.id || Tiles.decodeType( tile) == Tiles.Tile.TILE_TAR.id || Tiles.decodeType( tile) == Tiles.Tile.TILE_MOSS.id || Tiles.decodeType( tile) == Tiles.Tile.TILE_MYCELIUM.id){
                    return null;
                }

                if( performer instanceof Player && object.isDiggingtool() && !Terraforming.isNonDiggableTile(Tiles.decodeType(tile))){
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

            //this action is mostly taken from the 'level' action on dirt tiles.  it's simply applied to all four
            //corners of the tile down to the rock layer, and continues on non rock corners after other corners have
            //hit the rock layer.

            @Override
            public boolean action(Action action, Creature performer, Item source, int tilex, int tiley, boolean onSurface, int heightOffset, int tile, short num, float counter){

                try{
                    if( counter == 1.0f){
                        //can you reach the tile?
                        if(!performer.isOnSurface()) {
                            performer.getCommunicator().sendNormalServerMessage("You can not dig from the inside.");
                            return true;
                        }

                        //is this tile diggable?
                        byte type = Tiles.decodeType(tile);
                        if(Tiles.isSolidCave(type) || type == Tiles.Tile.TILE_CAVE.id || type == Tiles.Tile.TILE_CAVE_EXIT.id || type == Tiles.Tile.TILE_CLIFF.id || type == Tiles.Tile.TILE_ROCK.id || type == Tiles.Tile.TILE_CAVE_FLOOR_REINFORCED.id) {
                            performer.getCommunicator().sendNormalServerMessage("You can not dig in solid rock.");
                            return true;
                        }

                        //is the tile protected?
                        if(Zones.protectedTiles[tilex][tiley]) {
                            performer.getCommunicator().sendNormalServerMessage("Your body goes limp and you find no strength to continue here. Weird.");
                            return true;
                        }

                        performer.getCommunicator().sendNormalServerMessage("You start to clear away the soil");

                        String var37 = "sound.work.digging1";
                        int x = Server.rand.nextInt(3);
                        if(x == 0) {
                            var37 = "sound.work.digging2";
                        } else if(x == 1) {
                            var37 = "sound.work.digging3";
                        }
                        SoundPlayer.playSound(var37, performer, 0.0F);


                        int time = 50;
                        boolean nst = source.getTemplateId() == 176 && performer.getPower() >= 2; //ebony wand and GM check.
                        if( nst){ time = 1;}

                        performer.getCurrentAction().setTimeLeft(time);
                        performer.sendActionControl("Digging", true, time);
                    } else{
                        int time = performer.getCurrentAction().getTimeLeft();
                        if( counter * 10 > time){

                            dig(performer, source, tilex, tiley, counter, performer.isOnSurface()?Server.surfaceMesh:Server.caveMesh);
                            dig(performer, source, tilex + 1, tiley, counter, performer.isOnSurface()?Server.surfaceMesh:Server.caveMesh);
                            dig(performer, source, tilex, tiley + 1, counter, performer.isOnSurface()?Server.surfaceMesh:Server.caveMesh);
                            dig(performer, source, tilex + 1, tiley + 1, counter, performer.isOnSurface()?Server.surfaceMesh:Server.caveMesh);

                            performer.getCommunicator().sendNormalServerMessage("You finish digging.");
                            return true;
                        }
                    }
                    return false;
                } catch (Exception e){
                    logger.log( Level.WARNING, e.getMessage(), e);
                    return true;
                }
            }
        };
    }

    //again, most of this dig function was lifted from the 'level' action on dirt tiles..... or was it all of it...

    private static boolean dig(Creature performer, Item source, int tilex, int tiley, float counter, MeshIO mesh) {
        boolean done;

        try {
            boolean nst = source.getTemplateId() == 176 && performer.getPower() >= 2; //ebony wand and GM check.

            Skills skills = performer.getSkills();
            Skill digging;

            try {
                digging = skills.getSkill(1009);
            } catch (Exception var62) {
                digging = skills.learn(1009, 0.0F);
            }

            if(tilex < 0 || tilex > 1 << Constants.meshSize || tiley < 0 || tiley > 1 << Constants.meshSize) {
                performer.getCommunicator().sendNormalServerMessage("The water is too deep to dig.");
                return true;
            }

            int digTile = mesh.getTile(tilex, tiley);
            byte type = Tiles.decodeType(digTile);
            short currentTileHeight = Tiles.decodeHeight(digTile);
            int currentTileRock = Server.rockMesh.getTile(tilex, tiley);
            short currentRockHeight = Tiles.decodeHeight(currentTileRock);
            int encodedTile;
            int checkX;

            if(currentTileHeight <= currentRockHeight) {
                performer.getCommunicator().sendNormalServerMessage("You can not dig in the solid rock.");

                for(int var68 = 0; var68 >= -1; --var68) {
                    for(encodedTile = 0; encodedTile >= -1; --encodedTile) {
                        checkX = mesh.getTile(tilex + var68, tiley + encodedTile);
                        byte var69 = Tiles.decodeType(checkX);
                        boolean var71 = Terraforming.allCornersAtRockLevel(tilex + var68, tiley + encodedTile, mesh);
                        if(!isRockTile(var69) && !isImmutableTile(var69) && var71 && !Tiles.isTree(type) && !Tiles.isBush(type)) {
                            float var70 = Tiles.decodeHeightAsFloat(checkX);
                            Server.modifyFlagsByTileType(tilex + var68, tiley + encodedTile, Tiles.Tile.TILE_ROCK.id);
                            mesh.setTile(tilex + var68, tiley + encodedTile, Tiles.encode(var70, Tiles.Tile.TILE_ROCK.id, (byte)0));
                            Players.getInstance().sendChangedTile(tilex + var68, tiley + encodedTile, performer.isOnSurface(), true);
                        }
                    }
                }

                return true;
            }

            Village village;
            encodedTile = Server.surfaceMesh.getTile(tilex, tiley);
            village = Zones.getVillage(tilex, tiley, performer.isOnSurface());
            checkX = tilex;
            int checkY = tiley;
            if(village == null) {
                checkX = (int)performer.getStatus().getPositionX() - 2 >> 2;
                village = Zones.getVillage(checkX, tiley, performer.isOnSurface());
            }

            if(village == null) {
                checkY = (int)performer.getStatus().getPositionY() - 2 >> 2;
                village = Zones.getVillage(checkX, checkY, performer.isOnSurface());
            }

            if(village == null) {
                checkX = (int)performer.getStatus().getPositionX() + 2 >> 2;
                village = Zones.getVillage(checkX, checkY, performer.isOnSurface());
            }

            if(village != null && !village.isActionAllowed((short)144, performer, false, encodedTile, 0)) {
                if(!Zones.isOnPvPServer(tilex, tiley)) {
                    performer.getCommunicator().sendNormalServerMessage("This action is not allowed here, because the tile is on a player owned deed that has disallowed it.", (byte)3);
                    return true;
                }

                if(!village.isEnemy(performer) && performer.isLegal()) {
                    performer.getCommunicator().sendNormalServerMessage("That would be illegal here. You can check the settlement token for the local laws.", (byte)3);
                    return true;
                }
            }

            short digTileHeight = Tiles.decodeHeight(digTile);
            int rockTile;
            short rockHeight;
            short h = Tiles.decodeHeight(digTile);
            short minHeight = -7;
            short maxHeight = 20000;

            try {
                digging = skills.getSkill(1009);
            } catch (Exception var62) {
                digging = skills.learn(1009, 0.0F);
            }

            if(nst) {
                minHeight = -260;
            }

            if(h > minHeight && h < maxHeight) {
                done = false;
                Skill var72 = null;
                double power = 0.0D;
                if(!nst) {
                    try {
                        var72 = skills.getSkill(source.getPrimarySkill());
                    } catch (Exception var66) {
                        try {
                            var72 = skills.learn(source.getPrimarySkill(), 1.0F);
                        } catch (NoSuchSkillException var65) {
                            if(performer.getPower() <= 0) {
                                logger.log(Level.WARNING, performer.getName() + " trying to dig with an item with no primary skill: " + source.getName());
                            }
                        }
                    }
                }

                int nonDiggables = 0;
                byte difference = 0;
                short maxdifference = 0;
                if(!nst) {
                    if(checkIfTerraformingOnPermaObject(tilex, tiley)) {
                        performer.getCommunicator().sendNormalServerMessage("The object nearby prevents digging further down.");
                        return true;
                    }

                    if(Zones.isTileCornerProtected(tilex, tiley)) {
                        performer.getCommunicator().sendNormalServerMessage("Your shovel fails to penetrate the earth no matter what you try. Weird.");
                        return true;
                    }

                    if(Terraforming.isTileModBlocked(performer, tilex, tiley, true)) {
                        return true;
                    }

                    if(digTileHeight > 0 && Terraforming.wouldDestroyCobble(performer, tilex, tiley, false)) {
                        performer.getCommunicator().sendNormalServerMessage("The road would be too steep to traverse.");
                        return true;
                    }

                    int act = mesh.getTile(tilex, tiley);
                    if(Terraforming.checkDigTile(act, performer, digging, digTileHeight, difference)) {
                        return true;
                    }

                    if(Terraforming.isNonDiggableTile(Tiles.decodeType(act))) {
                        ++nonDiggables;
                    }

                    if(Zones.protectedTiles[tilex - 1][tiley]) {
                        performer.getCommunicator().sendNormalServerMessage("Your shovel fails to penetrate the earth no matter what you try. Weird.");
                        return true;
                    }

                    act = mesh.getTile(tilex - 1, tiley);
                    short time = Tiles.decodeHeight(act);
                    short var73 = (short)Math.abs(time - digTileHeight);
                    if(var73 > maxdifference) {
                        maxdifference = var73;
                    }

                    if(Terraforming.checkDigTile(act, performer, digging, digTileHeight, var73)) {
                        return true;
                    }

                    if(Terraforming.isNonDiggableTile(Tiles.decodeType(act))) {
                        ++nonDiggables;
                    }

                    if(Zones.protectedTiles[tilex][tiley + 1]) {
                        performer.getCommunicator().sendNormalServerMessage("Your shovel fails to penetrate the earth no matter what you try. Weird.");
                        return true;
                    }

                    act = mesh.getTile(tilex, tiley + 1);
                    time = Tiles.decodeHeight(act);
                    var73 = (short)Math.abs(time - digTileHeight);
                    if(var73 > maxdifference) {
                        maxdifference = var73;
                    }

                    if(Terraforming.checkDigTile(act, performer, digging, digTileHeight, var73)) {
                        return true;
                    }

                    if(Terraforming.isNonDiggableTile(Tiles.decodeType(act))) {
                        ++nonDiggables;
                    }

                    if(Zones.protectedTiles[tilex][tiley - 1]) {
                        performer.getCommunicator().sendNormalServerMessage("Your shovel fails to penetrate the earth no matter what you try. Weird.");
                        return true;
                    }

                    act = mesh.getTile(tilex, tiley - 1);
                    time = Tiles.decodeHeight(act);
                    var73 = (short)Math.abs(time - digTileHeight);
                    if(var73 > maxdifference) {
                        maxdifference = var73;
                    }

                    if(Terraforming.checkDigTile(act, performer, digging, digTileHeight, var73)) {
                        return true;
                    }

                    if(Terraforming.isNonDiggableTile(Tiles.decodeType(act))) {
                        ++nonDiggables;
                    }

                    if(Zones.protectedTiles[tilex + 1][tiley]) {
                        performer.getCommunicator().sendNormalServerMessage("Your shovel fails to penetrate the earth no matter what you try. Weird.");
                        return true;
                    }

                    act = mesh.getTile(tilex + 1, tiley);
                    time = Tiles.decodeHeight(act);
                    var73 = (short)Math.abs(time - digTileHeight);
                    if(var73 > maxdifference) {
                        maxdifference = var73;
                    }

                    if(Terraforming.checkDigTile(act, performer, digging, digTileHeight, var73)) {
                        return true;
                    }

                    if(Terraforming.isNonDiggableTile(Tiles.decodeType(act))) {
                        ++nonDiggables;
                    }
                }

                if(nonDiggables > 3) {
                    performer.getCommunicator().sendNormalServerMessage("You cannot dig in such terrain.");
                    return true;
                }

                Action var74;

                try {
                    var74 = performer.getCurrentAction();
                } catch (NoSuchActionException var60) {
                    logger.log(Level.WARNING, "Weird: " + var60.getMessage(), var60);
                    return true;
                }

                int var75 = 1000;
                int hitRock = 0;

                while(true) {
                    int dealDirt;
                    if(hitRock < -1) {
                        if(counter == 1.0F && !nst) {

                            var75 = Actions.getStandardActionTime(performer, digging, source, 0.0D);
                            var74.setTimeLeft(var75);
                            performer.getCommunicator().sendNormalServerMessage("You start to dig.");
                            Server.getInstance().broadCastAction(performer.getName() + " starts to dig.", performer, 5);
                            performer.sendActionControl(Actions.actionEntrys[144].getVerbString(), true, var75);
                            performer.getStatus().modifyStamina(-1000.0F);

                            source.setDamage(source.getDamage() + 0.0015F * source.getDamageModifier());
                        } else if(!nst) {
                            var75 = var74.getTimeLeft();
                            if(var74.justTickedSecond() && (var75 < 50 && var74.currentSecond() % 2 == 0 || var74.currentSecond() % 5 == 0)) {
                                String var76 = "sound.work.digging1";
                                dealDirt = Server.rand.nextInt(3);
                                if(dealDirt == 0) {
                                    var76 = "sound.work.digging2";
                                } else if(dealDirt == 1) {
                                    var76 = "sound.work.digging3";
                                }

                                SoundPlayer.playSound(var76, performer, 0.0F);
                                performer.getStatus().modifyStamina(-5000.0F);
                                source.setDamage(source.getDamage() + 0.0015F * source.getDamageModifier());
                            }
                        }

                        if(counter * 10.0F > (float)var75 || nst) {
                            int var79;
                            if(!nst) {
                                if(var74.getRarity() != 0) {
                                    performer.playPersonalSound("sound.fx.drumroll");
                                }

                                double var78 = (double)(1 + maxdifference / 5);
                                if(type == Tiles.Tile.TILE_CLAY.id) {
                                    var78 += 20.0D;
                                } else if(type == Tiles.Tile.TILE_SAND.id) {
                                    var78 += 10.0D;
                                } else if(type == Tiles.Tile.TILE_TAR.id) {
                                    var78 += 35.0D;
                                } else if(type == Tiles.Tile.TILE_MOSS.id) {
                                    var78 += 10.0D;
                                } else if(type == Tiles.Tile.TILE_MARSH.id) {
                                    var78 += 30.0D;
                                } else if(type == Tiles.Tile.TILE_STEPPE.id) {
                                    var78 += 40.0D;
                                } else if(type == Tiles.Tile.TILE_TUNDRA.id) {
                                    var78 += 20.0D;
                                }

                                if(var72 != null) {
                                    var72.skillCheck(var78, source, 0.0D, false, counter);
                                }

                                power = digging.skillCheck(var78, source, 0.0D, false, counter);
                                if(power < 0.0D) {
                                    for(var79 = 0; var79 < 20; ++var79) {
                                        power = digging.skillCheck(var78, source, 0.0D, true, 1.0F);
                                        if(power > 1.0D) {
                                            break;
                                        }

                                        power = 1.0D;
                                    }
                                }
                            }

                            done = true;
                            boolean var82 = false;
                            boolean var77 = false;
                            short var83 = 30000;
                            boolean var84 = false;
                            boolean var86 = false;
                            boolean var87 = false;
                            boolean dealTar = false;
                            boolean dealMoss = false;
                            int clayNum = Server.getDigCount(tilex, tiley);
                            if(clayNum <= 0 || clayNum > 100) {
                                clayNum = 50 + Server.rand.nextInt(50);
                            }

                            boolean allCornersRock;

                            for(int fe = 0; fe >= -1; --fe) {
                                for(int created = 0; created >= -1; --created) {
                                    boolean ql = false;
                                    var79 = mesh.getTile(tilex + fe, tiley + created);
                                    type = Tiles.decodeType(var79);
                                    short var80 = Tiles.decodeHeight(var79);
                                    rockTile = Server.rockMesh.getTile(tilex + fe, tiley + created);
                                    rockHeight = Tiles.decodeHeight(rockTile);
                                    if(fe == 0 && created == 0) {
                                        if(type == Tiles.Tile.TILE_CLAY.id) {
                                            var84 = true;
                                        } else if(type == Tiles.Tile.TILE_SAND.id) {
                                            var86 = true;
                                        } else if(type == Tiles.Tile.TILE_PEAT.id) {
                                            var87 = true;
                                        } else if(type == Tiles.Tile.TILE_TAR.id) {
                                            dealTar = true;
                                        } else if(type == Tiles.Tile.TILE_MOSS.id) {
                                            dealMoss = true;
                                        }

                                        if(var80 > rockHeight) {
                                            var77 = true;
                                            ql = true;
                                            if(var84) {
                                                --clayNum;
                                                if(clayNum == 0) {
                                                    var80 = (short)Math.max(var80 - 1, rockHeight);
                                                }
                                            } else if(!dealTar && !dealMoss && !var87) {
                                                var80 = (short)Math.max(var80 - 1, rockHeight);
                                            }

                                            if(nst) {
                                                performer.getCommunicator().sendNormalServerMessage("Tile " + (tilex + fe) + ", " + (tiley + created) + " now at " + var80 + ", rock at " + rockHeight + ".");
                                            }

                                            var83 = var80;
                                            mesh.setTile(tilex + fe, tiley + created, Tiles.encode(var80, type, Tiles.decodeData(var79)));
                                            if(performer.fireTileLog()) {
                                                TileEvent.log(tilex + fe, tiley + created, 0, performer.getWurmId(), 144);
                                            }
                                        }

                                        if(var80 <= rockHeight) {
                                            var82 = true;
                                        }
                                    }

                                    allCornersRock = Terraforming.allCornersAtRockLevel(tilex + fe, tiley + created, mesh);
                                    if(!isImmutableTile(type) && allCornersRock) {
                                        ql = true;
                                        Server.modifyFlagsByTileType(tilex + fe, tiley + created, Tiles.Tile.TILE_ROCK.id);
                                        mesh.setTile(tilex + fe, tiley + created, Tiles.encode(var80, Tiles.Tile.TILE_ROCK.id, (byte)0));
                                        TileEvent.log(tilex + fe, tiley + created, 0, performer.getWurmId(), 144);
                                    } else if(isTileTurnToDirt(type)) {
                                        if(type != Tiles.Tile.TILE_DIRT.id) {
                                            TileEvent.log(tilex + fe, tiley + created, 0, performer.getWurmId(), 144);
                                        }

                                        ql = true;
                                        Server.modifyFlagsByTileType(tilex + fe, tiley + created, Tiles.Tile.TILE_DIRT.id);
                                        mesh.setTile(tilex + fe, tiley + created, Tiles.encode(var80, Tiles.Tile.TILE_DIRT.id, (byte)0));
                                    } else if(Terraforming.isRoad(type)) {
                                        if(Methods.isActionAllowed(performer, (short)144, false, tilex + fe, tiley + created, digTile, 0)) {
                                            ql = true;
                                            Server.modifyFlagsByTileType(tilex + fe, tiley + created, type);
                                            mesh.setTile(tilex + fe, tiley + created, Tiles.encode(var80, type, Tiles.decodeData(var79)));
                                        }

                                        if(performer.fireTileLog()) {
                                            TileEvent.log(tilex + fe, tiley + created, 0, performer.getWurmId(), 144);
                                        }
                                    }

                                    if(performer.getTutorialLevel() == 8 && !performer.skippedTutorial()) {
                                        performer.missionFinished(true, true);
                                    }

                                    if(ql) {
                                        performer.getMovementScheme().touchFreeMoveCounter();
                                        Players.getInstance().sendChangedTile(tilex + fe, tiley + created, performer.isOnSurface(), true);

                                        try {
                                            Zone bone = Zones.getZone(tilex + fe, tiley + created, performer.isOnSurface());
                                            bone.changeTile(tilex + fe, tiley + created);
                                        } catch (NoSuchZoneException var59) {
                                            logger.log(Level.INFO, "no such zone?: " + tilex + ", " + tiley, var59);
                                        }
                                    }

                                    int gem;
                                    VolaTile atile;
                                    Item[] var91;
                                    if(performer.isOnSurface()) {
                                        var91 = EndGameItems.getArtifactDugUp(tilex + fe, tiley + created, (float)var80 / 10.0F, allCornersRock);
                                        if(var91.length > 0) {
                                            for(gem = 0; gem < var91.length; ++gem) {
                                                atile = Zones.getOrCreateTile(tilex + fe, tiley + created, performer.isOnSurface());
                                                atile.addItem(var91[gem], false, false);
                                                performer.getCommunicator().sendNormalServerMessage("You find something weird! You found the " + var91[gem].getName() + "!", (byte)2);
                                                logger.log(Level.INFO, performer.getName() + " found the " + var91[gem].getName() + " at tile " + (tilex + fe) + ", " + (tiley + created) + "! " + var91[gem]);
                                                HistoryManager.addHistory(performer.getName(), "reveals the " + var91[gem].getName());
                                                EndGameItem egi = EndGameItems.getEndGameItem(var91[gem]);
                                                if(egi != null) {
                                                    egi.setLastMoved(System.currentTimeMillis());
                                                    var91[gem].setAuxData((byte)120);
                                                }
                                            }
                                        }
                                    }

                                    var91 = Items.getHiddenItemsAt(tilex + fe, tiley + created, (float)var80 / 10.0F, true);
                                    if(var91.length > 0) {
                                        for(gem = 0; gem < var91.length; ++gem) {
                                            var91[gem].setHidden(false);
                                            Items.revealItem(var91[gem]);
                                            atile = Zones.getOrCreateTile(tilex + fe, tiley + created, performer.isOnSurface());
                                            atile.addItem(var91[gem], false, false);
                                            performer.getCommunicator().sendNormalServerMessage("You find something! You found a " + var91[gem].getName() + "!", (byte)2);
                                            logger.log(Level.INFO, performer.getName() + " found a " + var91[gem].getName() + " at tile " + (tilex + fe) + ", " + (tiley + created) + ".");
                                        }
                                    }
                                }
                            }

                            if(var84) {
                                Server.setDigCount(tilex, tiley, clayNum);
                            }

                            if(var82) {
                                performer.getCommunicator().sendNormalServerMessage("You hit rock.", (byte)3);
                            } else {
                                performer.getCommunicator().sendNormalServerMessage("You dig a hole.");
                                Server.getInstance().broadCastAction(performer.getName() + " digs a hole.", performer, 5);
                            }

                            if(var77) {
                                try {
                                    if(!nst) {
                                        double var88 = digging.getKnowledge(0.0D);
                                        if(power > var88) {
                                            power = var88;
                                        } else {
                                            power = Math.max(1.0D, power);
                                        }
                                    } else {
                                        power = 50.0D;
                                    }

                                    short var89 = 26; //dirt
                                    if(var86) {
                                        var89 = 298; //sand
                                    }

                                    Item var90 = ItemFactory.createItem(var89, Math.min((float)(power + (double)source.getRarity()), 100.0F), null);
                                    var90.setRarity(var74.getRarity());

                                    try {
                                        var90.putItemInfrontof(performer, 0.0f);
                                    } catch (Exception e){
                                        logger.log(Level.WARNING, "Exception #1 in ClearSoilAction.java :" + e.getMessage());
                                    }

                                    if(Server.isDirtHeightLower(tilex, tiley, var83)) {
                                        if(Server.rand.nextInt(2500) == 0) {
                                            short var92 = 374;
                                            if(Server.rand.nextFloat() * 100.0F >= 99.0F) {
                                                var92 = 375;
                                            }

                                            float var93 = Math.max(Math.min((float)(power + (double)source.getRarity()), 100.0F), 1.0F);
                                            Item var96 = ItemFactory.createItem(var92, var93, null);
                                            var96.setLastOwnerId(performer.getWurmId());
                                            var96.setRarity(var74.getRarity());
                                            if(var96.getQualityLevel() > 99.0F) {
                                                performer.achievement(363);
                                            } else if(var96.getQualityLevel() > 90.0F) {
                                                performer.achievement(364);
                                            }

                                            if(var74.getRarity() > 2) {
                                                performer.achievement(365);
                                            }

                                            performer.getInventory().insertItem(var96, true);
                                            performer.getCommunicator().sendNormalServerMessage("You find " + var96.getNameWithGenus() + "!", (byte)2);
                                        }

                                        if(var74.getRarity() != 0 && performer.isPaying() && Server.rand.nextInt(100) == 0) {
                                            float var95 = Math.max(Math.min((float)(power + (double)source.getRarity()), 100.0F), 1.0F);
                                            Item var94 = ItemFactory.createItem(867, var95, null);
                                            var94.setRarity(var74.getRarity());
                                            performer.getInventory().insertItem(var94, true);
                                            performer.getCommunicator().sendNormalServerMessage("You find something! You found a " + MethodsItems.getRarityName(var74.getRarity()) + " " + var94.getName() + "!", (byte)2);
                                            performer.achievement(366);
                                        }
                                    }
                                } catch (FailedException var63) {
                                    logger.log(Level.WARNING, var63.getMessage(), var63);
                                }
                            }
                        }
                        break;
                    }

                    for(dealDirt = 0; dealDirt >= -1; --dealDirt) {
                        try {
                            Zone lNewTile = Zones.getZone(tilex + hitRock, tiley + dealDirt, performer.isOnSurface());
                            VolaTile newTileHeight = lNewTile.getTileOrNull(tilex + hitRock, tiley + dealDirt);
                            if(newTileHeight != null) {
                                if(newTileHeight.getStructure() != null) {
                                    if(newTileHeight.getStructure().isTypeHouse()) {
                                        performer.getCommunicator().sendNormalServerMessage("The house is in the way.");
                                        return true;
                                    }

                                    BridgePart[] newDigHeight = newTileHeight.getBridgeParts();
                                    if(newDigHeight.length == 1) {
                                        if(newDigHeight[0].getType().isSupportType()) {
                                            performer.getCommunicator().sendNormalServerMessage("The bridge support nearby prevents digging.");
                                            return true;
                                        }

                                        if(hitRock == -1 && newDigHeight[0].hasEastExit() || hitRock == 0 && newDigHeight[0].hasWestExit() || dealDirt == -1 && newDigHeight[0].hasSouthExit() || dealDirt == 0 && newDigHeight[0].hasNorthExit()) {
                                            performer.getCommunicator().sendNormalServerMessage("The end of the bridge nearby prevents digging.");
                                            return true;
                                        }
                                    }
                                }

                                int dealClay;
                                Fence dealPeat;
                                Fence[] var81;
                                if(hitRock == 0 && dealDirt == 0) {
                                    var81 = newTileHeight.getFences();
                                    dealClay = var81.length;
                                    byte var85 = 0;
                                    if(var85 < dealClay) {
                                        dealPeat = var81[var85];
                                        performer.getCommunicator().sendNormalServerMessage("The " + dealPeat.getName() + " is in the way.");
                                        return true;
                                    }
                                } else {
                                    int dealSand;
                                    if(hitRock == -1 && dealDirt == 0) {
                                        var81 = newTileHeight.getFences();
                                        dealClay = var81.length;

                                        for(dealSand = 0; dealSand < dealClay; ++dealSand) {
                                            dealPeat = var81[dealSand];
                                            if(dealPeat.isHorizontal()) {
                                                performer.getCommunicator().sendNormalServerMessage("The " + dealPeat.getName() + " is in the way.");
                                                return true;
                                            }
                                        }
                                    } else if(dealDirt == -1 && hitRock == 0) {
                                        var81 = newTileHeight.getFences();
                                        dealClay = var81.length;

                                        for(dealSand = 0; dealSand < dealClay; ++dealSand) {
                                            dealPeat = var81[dealSand];
                                            if(!dealPeat.isHorizontal()) {
                                                performer.getCommunicator().sendNormalServerMessage("The " + dealPeat.getName() + " is in the way.");
                                                return true;
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (NoSuchZoneException var64) {
                            performer.getCommunicator().sendNormalServerMessage("The water is too deep to dig in.");
                            return true;
                        }

                        if(performer.getStrengthSkill() < 20.0D) {
                            int newTile = mesh.getTile(tilex + hitRock, tiley + dealDirt);
                            if(Terraforming.isRoad(Tiles.decodeType(newTile))) {
                                performer.getCommunicator().sendNormalServerMessage("You need to be stronger to dig on roads.");
                                return true;
                            }
                        }
                    }

                    --hitRock;
                }
            } else {
                done = true;
                if(h <= minHeight) {
                    performer.getCommunicator().sendNormalServerMessage("The water is too deep to dig.", (byte)3);
                } else {
                    performer.getCommunicator().sendNormalServerMessage("You do not have sufficient skill to dig at that height.", (byte)3);
                }
            }
        } catch (NoSuchTemplateException var67) {
            logger.log(Level.WARNING, var67.getMessage(), var67);
            done = true;
        }

        return done;
    }

    // had to bring these functions into the local scope to use them,  There's probably a better way.  remember to watch for updates on the originals of these.

    private static boolean isImmutableTile(byte type) {
        return Tiles.isTree(type) || Tiles.isBush(type) || type == Tiles.Tile.TILE_CLAY.id || type == Tiles.Tile.TILE_MARSH.id || type == Tiles.Tile.TILE_PEAT.id || type == Tiles.Tile.TILE_TAR.id || type == Tiles.Tile.TILE_PLANKS.id || type == Tiles.Tile.TILE_HOLE.id || type == Tiles.Tile.TILE_MOSS.id || type == Tiles.Tile.TILE_LAVA.id || Tiles.isMineDoor(type) || type == Tiles.Tile.TILE_PLANKS_TARRED.id;
    }

    private static boolean isTileTurnToDirt(byte type) {
        return type == Tiles.Tile.TILE_GRASS.id || type == Tiles.Tile.TILE_MYCELIUM.id || type == Tiles.Tile.TILE_STEPPE.id || type == Tiles.Tile.TILE_FIELD.id || type == Tiles.Tile.TILE_TUNDRA.id || type == Tiles.Tile.TILE_REED.id || type == Tiles.Tile.TILE_KELP.id || type == Tiles.Tile.TILE_LAWN.id || type == Tiles.Tile.TILE_MYCELIUM_LAWN.id;
    }

    private static boolean checkIfTerraformingOnPermaObject(int tilex, int tiley) {
        short hh = Tiles.decodeHeight(Server.surfaceMesh.getTile(tilex, tiley));
        if(hh < 1) {
            VolaTile t = Zones.getTileOrNull(tilex, tiley, true);
            if(t != null && t.hasOnePerTileItem(0)) {
                return true;
            }

            t = Zones.getTileOrNull(tilex - 1, tiley - 1, true);
            if(t != null && t.hasOnePerTileItem(0)) {
                return true;
            }

            t = Zones.getTileOrNull(tilex, tiley - 1, true);
            if(t != null && t.hasOnePerTileItem(0)) {
                return true;
            }

            t = Zones.getTileOrNull(tilex - 1, tiley, true);
            if(t != null && t.hasOnePerTileItem(0)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isRockTile(byte type) {
        return Tiles.isSolidCave(type) || type == Tiles.Tile.TILE_CAVE.id || type == Tiles.Tile.TILE_CAVE_EXIT.id || type == Tiles.Tile.TILE_CLIFF.id || type == Tiles.Tile.TILE_ROCK.id || type == Tiles.Tile.TILE_CAVE_FLOOR_REINFORCED.id;
    }
}
