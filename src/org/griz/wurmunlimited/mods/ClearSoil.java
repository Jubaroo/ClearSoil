package org.griz.wurmunlimited.mods;

/**
 * Created by John on 10/6/2016.  Updated 5/15/2018
 */

import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.wurmonline.server.creatures.Communicator;
import org.gotti.wurmunlimited.modloader.interfaces.*;
import org.gotti.wurmunlimited.modsupport.actions.ModActions;

public class ClearSoil implements WurmServerMod, PreInitable, ServerStartedListener, Configurable, PlayerMessageListener{

    private static Logger logger = Logger.getLogger(ClearSoil.class.getName());
    private static int Skill = 0;

    @Override
    public void configure(Properties properties){
        Skill = Integer.valueOf(properties.getProperty("MinSkill", Integer.toString(Skill)));
        logger.log(Level.INFO,"Clear Soil MinSkill = " + Skill);
    }

    //  '/ClearSoil' gives the player basic instructions on using the command.
    @Override
    public MessagePolicy onPlayerMessage(Communicator communicator, String message, String title){
        if( message.startsWith("/ClearSoil")){
        communicator.sendNormalServerMessage("The 'ClearSoil' action will only work on a diggable tile that you are currently standing on.  You must also have at least "+ Skill +" digging skill to clear soil.");
            return MessagePolicy.DISCARD;
        }
        return MessagePolicy.PASS;
    }

    // muh, abstract methods.  Just a throw away method to clean up the inheritance.
    @Override
    public boolean onPlayerMessage(Communicator communicator, String message){
        return false;
    }

    @Override
    public void onServerStarted(){
        logger.log(Level.INFO, "Registering ClearSoil actions");
        ModActions.registerAction( new ClearSoilAction( Skill));
        ModActions.registerAction( new SMFlattenAction( Skill));
        logger.log(Level.INFO, "Finished registering ClearSoil actions");
    }

    @Override
    public void preInit(){ModActions.init(); }
}