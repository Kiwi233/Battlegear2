package mods.battlegear2;

import mods.battlegear2.api.weapons.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.BaseAttributeMap;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSource;
import net.minecraft.util.EntityDamageSourceIndirect;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerUseItemEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Map;

/**
 * User: nerd-boy
 * Date: 30/07/13
 * Time: 12:36 PM
 * Events registered with MinecraftForge event bus on default priority: 
 * {@link LivingAttackEvent}, to perform weapons custom effects
 */
public final class WeaponHookContainerClass {

    public static final WeaponHookContainerClass INSTANCE = new WeaponHookContainerClass();
    private static final float backstabFuzzy = 0.01F;
    private static final int[] dazeEffects = {Potion.moveSlowdown.getId(), Potion.confusion.getId(), Potion.blindness.getId(), Potion.weakness.getId()};
    public boolean doBlocking = false;

    private WeaponHookContainerClass(){}

    @SubscribeEvent
    public void onAttack(LivingAttackEvent event){

    	if(event.entityLiving instanceof EntityPlayer && ((EntityPlayer)event.entityLiving).capabilities.isCreativeMode)
    	{
    		return;//Fix vanilla bug with baby zombies being able to lead mobs to attack player
    		//in creative mode thus calling the event
    	}
        EntityLivingBase entityHit = event.entityLiving;
        //Record the hurt times
        int hurtTimeTemp = entityHit.hurtTime;
        int hurtResistanceTimeTemp = entityHit.hurtResistantTime;
        if(event.source instanceof EntityDamageSource && !event.source.damageType.startsWith(Battlegear.CUSTOM_DAMAGE_SOURCE) &&
                !(event.source instanceof EntityDamageSourceIndirect) )
        {
            Entity attacker = event.source.getEntity();
            if(attacker instanceof EntityLivingBase)
            {
                EntityLivingBase entityHitting = (EntityLivingBase)attacker;
                ItemStack stack = entityHitting.getHeldItem();
                if(stack!=null)
                {
                    boolean hit=false;
                    if(stack.getItem() instanceof IBackStabbable)
                    {
                        hit = performBackStab(stack.getItem(), entityHit, entityHitting);
                    }
                    if(stack.getItem() instanceof ISpecialEffect)
                    {
                        boolean tempHit = ((ISpecialEffect)stack.getItem()).performEffects(entityHit,entityHitting);
                        if(!hit)
                            hit = tempHit;
                    }
                    if(stack.getItem() instanceof IPotionEffect)
                    {
                        performEffects(((IPotionEffect)stack.getItem()).getEffectsOnHit(entityHit, entityHitting), entityHit);
                    }
                    if(!entityHit.worldObj.isRemote) {
                        int timeModifier = (int) (-entityHitting.getEntityAttribute(Attributes.attackSpeed).getAttributeValue() * entityHit.maxHurtResistantTime * 0.5F);
                        if (stack.getItem() instanceof IHitTimeModifier) {
                            timeModifier = ((IHitTimeModifier) stack.getItem()).getHitTime(stack, entityHit);
                        }
                        if (timeModifier != 0) {
                            if (hurtResistanceTimeTemp > entityHit.maxHurtResistantTime * 0.5F) {//Hit shield is in effect
                                //If the shield is supposed to be reduced, don't re-apply the effect every time
                                if (timeModifier < 0 && entityHit.getAITarget() == entityHitting && entityHit.ticksExisted - entityHit.getLastAttackerTime() < -timeModifier) {
                                    return;
                                }
                                //Apply hit shield modifier
                                entityHit.hurtResistantTime = hurtResistanceTimeTemp + timeModifier;
                                if (entityHit.hurtResistantTime < 0)
                                    entityHit.hurtResistantTime = 0;
                            }
                        } else if (hit) {
                            //Re-apply the saved values
                            entityHit.hurtTime = hurtTimeTemp;
                            entityHit.hurtResistantTime = hurtResistanceTimeTemp;
                        }
                    }
                }
            }
        }
    }

    /**
     * Additional damage sources:
     * {@code Battlegear#CUSTOM_DAMAGE_SOURCE+".mounted"} to apply mounted bonus attribute
     * {@code DamageSource#generic} for penetrative weapons
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingHurt(LivingHurtEvent hurt){
        if(hurt.source.getEntity() instanceof EntityLivingBase && hurt.source instanceof EntityDamageSource && hurt.entityLiving.hurtTime == 0){
            EntityLivingBase source = ((EntityLivingBase) hurt.source.getEntity());
            double chance = source.getEntityAttribute(Attributes.daze).getAttributeValue();
            if (source.getRNG().nextDouble() < chance) {
                for (int effect : dazeEffects) {
                    if (!hurt.entityLiving.isPotionActive(effect)) {
                        PotionEffect potion = new PotionEffect(effect, 3 * 20, 100);
                        potion.getCurativeItems().clear();
                        hurt.entityLiving.addPotionEffect(potion);
                    }
                }
            }
            final int hurtResistanceTimeTemp = hurt.entityLiving.hurtResistantTime;
            if (!hurt.source.damageType.startsWith(Battlegear.CUSTOM_DAMAGE_SOURCE) && source.isRiding()) {
                float damage = (float) source.getEntityAttribute(Attributes.mountedBonus).getAttributeValue();
                if(damage>0){
                    hurt.entityLiving.hurtResistantTime = 0;
                    hurt.entityLiving.attackEntityFrom(new EntityDamageSource(Battlegear.CUSTOM_DAMAGE_SOURCE + ".mounted", source), damage);
                }
            }
            float damage = (float) source.getEntityAttribute(Attributes.armourPenetrate).getAttributeValue();
            ItemStack itemStack = source.getHeldItem();
            if(itemStack!=null && itemStack.getItem() instanceof IPenetrateWeapon) {
                damage = ((IPenetrateWeapon) itemStack.getItem()).getPenetratingPower(itemStack);
            }
            if (damage > 0) {
                hurt.entityLiving.hurtResistantTime = 0;
                //Attack using the "generic" damage type (ignores armour)
                hurt.entityLiving.attackEntityFrom(DamageSource.generic, damage);
            }
            hurt.entityLiving.hurtResistantTime = hurtResistanceTimeTemp;
        }
    }

    /**
     * Register the custom attributes
     */
    @SubscribeEvent
    public void onLivingConstructor(EntityEvent.EntityConstructing constructing){
        if(constructing.entity instanceof EntityLivingBase){
            BaseAttributeMap attributeMap = ((EntityLivingBase) constructing.entity).getAttributeMap();
            attributeMap.registerAttribute(Attributes.armourPenetrate);
            attributeMap.registerAttribute(Attributes.daze);
            if(constructing.entity instanceof EntityPlayer){
                attributeMap.registerAttribute(Attributes.extendedReach).setBaseValue(-2.2);//Reduce bare hands range
            }
            attributeMap.registerAttribute(Attributes.attackSpeed);
            attributeMap.registerAttribute(Attributes.mountedBonus);
        }
    }

    public boolean performBackStab(Item item, EntityLivingBase entityHit, EntityLivingBase entityHitting) {
        //Get victim and murderer vector views at hit time
        double[] victimView = new double[]{entityHit.getLookVec().xCoord,entityHit.getLookVec().zCoord};
        double[] murdererView = new double[]{entityHitting.getLookVec().xCoord,entityHitting.getLookVec().zCoord};
        //back-stab conditions: vectors are closely enough aligned, (fuzzy parameter might need testing)
        //but not in opposite directions (face to face or sideways)
        if(Math.abs(victimView[0]*murdererView[1]-victimView[1]*murdererView[0])<backstabFuzzy &&
                Math.signum(victimView[0])==Math.signum(murdererView[0]) &&
                Math.signum(victimView[1])==Math.signum(murdererView[1])){
            return ((IBackStabbable)item).onBackStab(entityHit, entityHitting);//Perform back stab effect
        }
        return false;
    }

    public void performEffects(Map<PotionEffect, Float> map, EntityLivingBase entityHit) {
        double roll = Math.random();
        for (Map.Entry<PotionEffect, Float> effect : map.entrySet()) {
            //add effects if they aren't already applied, with corresponding chance factor
            if (!entityHit.isPotionActive(effect.getKey().getPotionID()) && effect.getValue() > roll) {
                entityHit.addPotionEffect(new PotionEffect(effect.getKey()));
            }
        }
    }

    @SubscribeEvent
    public void onBlock(PlayerUseItemEvent.Start use) {
        if (!doBlocking && use.duration > 0 && use.item.getItemUseAction() == EnumAction.BLOCK) {
            use.setCanceled(true);
        }
    }
}
