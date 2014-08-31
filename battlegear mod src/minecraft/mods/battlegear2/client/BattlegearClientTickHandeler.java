package mods.battlegear2.client;

import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.internal.FMLProxyPacket;
import cpw.mods.fml.relauncher.Side;
import mods.battlegear2.Battlegear;
import mods.battlegear2.BattlemodeHookContainerClass;
import mods.battlegear2.api.core.BattlegearUtils;
import mods.battlegear2.api.core.IBattlePlayer;
import mods.battlegear2.api.quiver.QuiverArrowRegistry;
import mods.battlegear2.api.shield.IShield;
import mods.battlegear2.api.core.InventoryPlayerBattle;
import mods.battlegear2.api.weapons.IExtendedReachWeapon;
import mods.battlegear2.enchantments.BaseEnchantment;
import mods.battlegear2.packet.BattlegearAnimationPacket;
import mods.battlegear2.packet.BattlegearShieldBlockPacket;
import mods.battlegear2.packet.BattlegearSyncItemPacket;
import mods.battlegear2.packet.OffhandPlaceBlockPacket;
import mods.battlegear2.utils.EnumBGAnimations;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import org.lwjgl.input.Keyboard;

public class BattlegearClientTickHandeler {

    public static float blockBar = 1;
    public static boolean wasBlocking = false;
    public static final float[] COLOUR_DEFAULT = new float[]{0, 0.75F, 1};
    public static final float[] COLOUR_RED = new float[]{1, 0.1F, 0.1F};
    public static final float[] COLOUR_YELLOW = new float[]{1, 1F, 0.1F};
    private static final int FLASH_MAX = 30;
    private static int flashTimer;

    public static float partialTick;

    public static KeyBinding drawWeapons = new KeyBinding("Draw Weapons", Keyboard.KEY_R, "key.categories.gameplay");
    public static KeyBinding special = new KeyBinding("Special", Keyboard.KEY_Z, "key.categories.gameplay");

    private static int previousNormal = 0;
    public static int previousBattlemode = InventoryPlayerBattle.OFFSET;
    private boolean specialDone = false, drawDone = false;
    private boolean inBattle = false, forceItemUse = false;
    private Minecraft mc;
    public BattlegearClientTickHandeler(){
        ClientRegistry.registerKeyBinding(drawWeapons);
        ClientRegistry.registerKeyBinding(special);
        mc = FMLClientHandler.instance().getClient();
    }

    @SubscribeEvent
    public void keyDown(TickEvent.ClientTickEvent event) {

        if(Battlegear.battlegearEnabled){
            //null checks to prevent any crash outside the world (and to make sure we have no screen open)
            if (mc.thePlayer != null && mc.theWorld != null && mc.currentScreen == null) {
                EntityClientPlayerMP player = mc.thePlayer;
                if(event.phase == TickEvent.Phase.START) {
                    if (!specialDone && special.getIsKeyPressed() && ((IBattlePlayer) player).getSpecialActionTimer() == 0) {
                        ItemStack quiver = QuiverArrowRegistry.getArrowContainer(player.getCurrentEquippedItem(), player);

                        if (quiver != null) {
                            FMLProxyPacket p = new BattlegearAnimationPacket(EnumBGAnimations.SpecialAction, player).generatePacket();
                            Battlegear.packetHandler.sendPacketToServer(p);
                            ((IBattlePlayer) player).setSpecialActionTimer(2);
                        } else if (((IBattlePlayer) player).isBattlemode()) {
                            ItemStack offhand = ((InventoryPlayerBattle) player.inventory).getCurrentOffhandWeapon();

                            if (offhand != null && offhand.getItem() instanceof IShield) {
                                float shieldBashPenalty = 0.33F - 0.06F * EnchantmentHelper.getEnchantmentLevel(BaseEnchantment.bashWeight.effectId, offhand);

                                if (BattlegearClientTickHandeler.blockBar >= shieldBashPenalty) {
                                    FMLProxyPacket p = new BattlegearAnimationPacket(EnumBGAnimations.SpecialAction, player).generatePacket();
                                    Battlegear.packetHandler.sendPacketToServer(p);
                                    ((IBattlePlayer) player).setSpecialActionTimer(((IShield) offhand.getItem()).getBashTimer(offhand));

                                    BattlegearClientTickHandeler.blockBar -= shieldBashPenalty;
                                }
                            }else{
                                forceItemUse = !forceItemUse;
                                player.addChatMessage(new ChatComponentText("Forced item usage: " + forceItemUse));
                            }
                        }
                        specialDone = true;
                    } else if (specialDone && !special.getIsKeyPressed()) {
                        specialDone = false;
                    }
                    if (!drawDone && drawWeapons.getIsKeyPressed()) {
                        if (((IBattlePlayer) player).isBattlemode()) {
                            previousBattlemode = player.inventory.currentItem;
                            player.inventory.currentItem = previousNormal;
                        } else {
                            previousNormal = player.inventory.currentItem;
                            player.inventory.currentItem = previousBattlemode;
                        }
                        mc.playerController.syncCurrentPlayItem();
                        drawDone = true;
                    } else if (drawDone && !drawWeapons.getIsKeyPressed()) {
                        drawDone = false;
                    }
                    inBattle = ((IBattlePlayer) player).isBattlemode();
                }else {
                    if(inBattle && !((IBattlePlayer) player).isBattlemode()){
                        for (int i = 0; i < InventoryPlayerBattle.WEAPON_SETS; ++i){
                            if (mc.gameSettings.keyBindsHotbar[i].getIsKeyPressed()){
                                previousBattlemode = InventoryPlayerBattle.OFFSET + i;
                            }
                        }
                        player.inventory.currentItem = previousBattlemode;
                        mc.playerController.syncCurrentPlayItem();
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event){
        if(event.phase == TickEvent.Phase.START){
            tickStart(mc.thePlayer);
        }else{
            tickEnd(mc.thePlayer);
        }
    }

    public void tickStart(EntityPlayer player) {
        if(((IBattlePlayer)player).isBattlemode()){
            ItemStack offhand = ((InventoryPlayerBattle) player.inventory).getCurrentOffhandWeapon();
            if(offhand != null){
                if(offhand.getItem() instanceof IShield){
                    if(flashTimer == FLASH_MAX){
                        player.motionY = player.motionY/2;
                    }
                    if(flashTimer > 0){
                        flashTimer --;
                    }
                    if(mc.gameSettings.keyBindUseItem.getIsKeyPressed() && !player.isSwingInProgress){
                        blockBar -= ((IShield) offhand.getItem()).getDecayRate(offhand);
                        if(blockBar > 0){
                            if(!wasBlocking){
                                Battlegear.packetHandler.sendPacketToServer(new BattlegearShieldBlockPacket(true, player).generatePacket());
                            }
                            wasBlocking = true;
                        }else{
                            if(wasBlocking){
                                //Send packet
                                Battlegear.packetHandler.sendPacketToServer(new BattlegearShieldBlockPacket(false, player).generatePacket());
                            }
                            wasBlocking = false;
                            blockBar = 0;
                        }
                    }else{
                        if(wasBlocking){
                            //send packet
                            Battlegear.packetHandler.sendPacketToServer(new BattlegearShieldBlockPacket(false, player).generatePacket());
                        }
                        wasBlocking = false;
                        blockBar += ((IShield) offhand.getItem()).getRecoveryRate(offhand);
                        if(blockBar > 1){
                            blockBar = 1;
                        }
                    }
                }else if(doesUseReplaceAttack(offhand)){
                    if(mc.gameSettings.keyBindUseItem.getIsKeyPressed() && !player.isSwingInProgress){
                        MovingObjectPosition mouseOver = mc.objectMouseOver;
                        boolean flag = true;
                        if (mouseOver != null)
                        {
                            if (mouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY)
                            {
                                if(mc.playerController.interactWithEntitySendPacket(player, mouseOver.entityHit))
                                    flag = false;
                            }
                            else if (mouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK)
                            {
                                int j = mouseOver.blockX;
                                int k = mouseOver.blockY;
                                int l = mouseOver.blockZ;
                                if (!player.worldObj.getBlock(j, k, l).isAir(player.worldObj, j, k, l)) {
                                    final int size = offhand.stackSize;
                                    int i1 = mouseOver.sideHit;

                                    boolean result = !ForgeEventFactory.onPlayerInteract(player, PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK, j, k, l, i1, player.worldObj).isCanceled();
                                    if (result && onPlayerPlaceBlock(mc.playerController, player, offhand, j, k, l, i1, mouseOver.hitVec)) {
                                        ((IBattlePlayer) player).swingOffItem();
                                        flag = false;
                                    }
                                    if (offhand == null)
                                    {
                                        return;
                                    }
                                    if (offhand.stackSize == 0)
                                    {
                                        BattlegearUtils.setPlayerOffhandItem(player, null);
                                    }
                                    else if (offhand.stackSize != size || mc.playerController.isInCreativeMode())
                                    {
                                        mc.entityRenderer.itemRenderer.resetEquippedProgress();
                                    }
                                }
                            }
                        }
                        if (flag)
                        {
                            offhand = ((InventoryPlayerBattle) player.inventory).getCurrentOffhandWeapon();
                            boolean result = !ForgeEventFactory.onPlayerInteract(player, PlayerInteractEvent.Action.RIGHT_CLICK_AIR, 0, 0, 0, -1, player.worldObj).isCanceled();
                            if (offhand != null && result && BattlemodeHookContainerClass.tryUseItem(player, offhand, Side.CLIENT))
                            {
                                mc.entityRenderer.itemRenderer.resetEquippedProgress2();
                            }
                        }
                    }
                }
            }
        }
    }

    public boolean doesUseReplaceAttack(ItemStack offhand) {
        return forceItemUse || offhand.getItem() instanceof ItemBlock;
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event){
        if(event.phase == TickEvent.Phase.START){
            partialTick = event.renderTickTime;
            if(mc.currentScreen instanceof GuiMainMenu){
                Battlegear.battlegearEnabled = false;
            }
        }
    }

    private boolean onPlayerPlaceBlock(PlayerControllerMP controller, EntityPlayer player, ItemStack offhand, int i, int j, int k, int l, Vec3 hitVec) {
        float f = (float)hitVec.xCoord - (float)i;
        float f1 = (float)hitVec.yCoord - (float)j;
        float f2 = (float)hitVec.zCoord - (float)k;
        boolean flag = false;
        int i1;
        final World worldObj = player.worldObj;
        if (offhand.getItem().onItemUseFirst(offhand, player, worldObj, i, j, k, l, f, f1, f2)){
            return true;
        }
        if (!player.isSneaking() || ((InventoryPlayerBattle) player.inventory).getCurrentOffhandWeapon() == null || ((InventoryPlayerBattle) player.inventory).getCurrentOffhandWeapon().getItem().doesSneakBypassUse(worldObj, i, j, k, player)){
            Block b = worldObj.getBlock(i, j, k);
            if (!b.isAir(worldObj, i, j, k) && b.onBlockActivated(worldObj, i, j, k, player, l, f, f1, f2)){
                flag = true;
            }
        }
        if (!flag && offhand.getItem() instanceof ItemBlock){
            ItemBlock itemblock = (ItemBlock)offhand.getItem();
            if (!itemblock.func_150936_a(worldObj, i, j, k, l, player, offhand)){
                return false;
            }
        }
        if (flag){
            return true;
        }
        else if (offhand == null){
            return false;
        }
        else{
            Battlegear.packetHandler.sendPacketToServer(new OffhandPlaceBlockPacket(i, j, k, l, offhand, f, f1, f2).generatePacket());
            if (controller.isInCreativeMode()){
                i1 = offhand.getItemDamage();
                int j1 = offhand.stackSize;
                boolean flag1 = offhand.tryPlaceItemIntoWorld(player, worldObj, i, j, k, l, f, f1, f2);
                offhand.setItemDamage(i1);
                offhand.stackSize = j1;
                return flag1;
            }
            else{
                if (!offhand.tryPlaceItemIntoWorld(player, worldObj, i, j, k, l, f, f1, f2)){
                    return false;
                }
                if (offhand.stackSize <= 0){
                    ForgeEventFactory.onPlayerDestroyItem(player, offhand);
                    BattlegearUtils.setPlayerOffhandItem(player, null);
                }
                Battlegear.packetHandler.sendPacketToServer(new BattlegearSyncItemPacket(player).generatePacket());
                return true;
            }
        }
    }

    public void tickEnd(EntityPlayer player) {
        ItemStack offhand = ((InventoryPlayerBattle) player.inventory).getCurrentOffhandWeapon();
        Battlegear.proxy.tryUseDynamicLight(player, offhand);
        //If we use a shield
        if(offhand != null && offhand.getItem() instanceof IShield){
            if(mc.gameSettings.keyBindUseItem.getIsKeyPressed() && !player.isSwingInProgress && blockBar > 0){
                player.motionX = player.motionX/5;
                player.motionZ = player.motionZ/5;
            }
        }

        //If we JUST swung an Item
        if (player.swingProgressInt == 1) {
            ItemStack mainhand = player.getCurrentEquippedItem();
            if (mainhand != null && mainhand.getItem() instanceof IExtendedReachWeapon) {
                float extendedReach = ((IExtendedReachWeapon) mainhand.getItem()).getReachModifierInBlocks(mainhand);
                if(extendedReach > 0){
                    MovingObjectPosition mouseOver = Battlegear.proxy.getMouseOver(partialTick, extendedReach + mc.playerController.getBlockReachDistance());
                    if (mouseOver != null && mouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY) {
                        Entity target = mouseOver.entityHit;
                        if (target instanceof EntityLivingBase && target != player && player.getDistanceToEntity(target) > mc.playerController.getBlockReachDistance()) {
                            if (target.hurtResistantTime != ((EntityLivingBase) target).maxHurtResistantTime) {
                                mc.playerController.attackEntity(player, target);
                            }
                        }
                    }
                }
            }
        }
    }

    public static void resetFlash(){
        flashTimer = FLASH_MAX;
    }

    public static int getFlashTimer(){
        return flashTimer;
    }
}
