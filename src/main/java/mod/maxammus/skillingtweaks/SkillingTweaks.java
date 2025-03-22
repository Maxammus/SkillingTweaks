package mod.maxammus.skillingtweaks;

import javassist.*;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.Configurable;
import org.gotti.wurmunlimited.modloader.interfaces.Initable;
import org.gotti.wurmunlimited.modloader.interfaces.PreInitable;
import org.gotti.wurmunlimited.modloader.interfaces.WurmServerMod;

import java.util.Properties;
import java.util.logging.Logger;


public class SkillingTweaks implements WurmServerMod, Configurable, Initable, PreInitable {
    private static final Logger logger = Logger.getLogger(SkillingTweaks.class.getName());
    private static ClassPool classPool;

    public static boolean timedSkilling = true;
    public static boolean onlineLike = false;
    public static float difficultyScaling = 0.1f;
    public static boolean debugging = false;
    @Override
    public void configure(Properties properties) {
        try {
            timedSkilling = Boolean.parseBoolean(properties.getProperty("timedSkilling", Boolean.toString(timedSkilling)));
            onlineLike = Boolean.parseBoolean(properties.getProperty("onlineLike", Boolean.toString(onlineLike)));
            difficultyScaling = Float.parseFloat(properties.getProperty("difficultyScaling", Float.toString(difficultyScaling)));
            debugging = Boolean.parseBoolean(properties.getProperty("debugging", Boolean.toString(debugging)));

            logger.info("timedSkilling: " + timedSkilling);
            logger.info("onlineLike: " + onlineLike);
            logger.info("difficultyScaling: " + difficultyScaling);
            logger.info("debugging: " + debugging);
        } catch (Exception e) {
            logger.severe("Error while reading serf mod configuration.");
            throw new RuntimeException(e);
        }
    }

    @Override
    public void preInit() {
        WurmServerMod.super.preInit();
        classPool = HookManager.getInstance().getClassPool();
    }

    @Override
    public void init() {
        try {
            CtClass skillClass = classPool.getCtClass("com.wurmonline.server.skills.Skill");

            if(timedSkilling) {
                logger.info("Adding action time back to skill ticks");
                skillClass.getMethod("skillCheck", "(DDZF)D")
                        .setBody("return skillCheck($$, true, 2.0);");
                skillClass.getMethod("skillCheck", "(DLcom/wurmonline/server/items/Item;DZF)D")
                        .setBody("return skillCheck($$, true, 2.0, null, null);");
                skillClass.getMethod("skillCheck", "(DDZFLcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/creatures/Creature;)D")
                        .setBody("return skillCheck($1, $2, $3, $4, true, 2.0, $5, $6);");
                skillClass.getMethod("skillCheck", "(DLcom/wurmonline/server/items/Item;DZFLcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/creatures/Creature;)D")
                        .setBody("return skillCheck($1, $2, $3, $4, $5, true, 2.0, $6, $7);");
            }

            String powerCheck = "";
            String scaling = "";
            String debug = "";
            CtMethod doSkillGainNew = skillClass.getDeclaredMethod("doSkillGainNew");
            if(onlineLike){
                logger.info("Limiting skillgain to power 0-40");
                powerCheck = "if(power < 0.0  || (power > 40.0 && knowledge > 20.0)) return;";
            }
            if(difficultyScaling > 0) {
                logger.info("Improving skillgain for higher difficulties");
                //Multiply skillgain for every level of difficulty over 5
                //check power to avoid people using super high diff actions to gain skills extra fast on failures before 20
                scaling = "if(power > 0) learnMod *= 1 + (Math.max(5.0, check) - 5) * " + difficultyScaling + "; ";
            }
            if(debugging) {
                logger.info("Enabling skilling debug");
                debug = "com.wurmonline.server.Players.getInstance().getPlayer(parent.getId()).getCommunicator()" +
                        "   .sendMessage(new com.wurmonline.server.Message(null, (byte)0, \"Skill Debug\", " +
//                        "java.lang.String.format(\"%s: diff: %.2f, power: %.2f, learnMod: %.2f, times: %.2f, skillDivider: %.2f\", new Object[] { getName(), $1, $2, $3, $4, $5 })));";
                        "       mod.maxammus.skillingtweaks.SkillingTweaks.debugString(getName(), check, power, learnMod, times, skillDivider)));";
            }

            doSkillGainNew.insertBefore("{ " + scaling + debug + powerCheck + "}");

        } catch (NotFoundException | CannotCompileException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getVersion() {
        return "0.1";
    }

    //I give up trying to call varargs methods with javassist
    public static String debugString(String skill, double diff, double power, double learnmod, float timer, double skillDivider) {
        return String.format("diff: %03.2f, power: %03.2f, learnMod: %03.2f, times: %03.2f, skillDivider: %03.2f - %s", diff, power, learnmod, timer, skillDivider, skill);
    }
}