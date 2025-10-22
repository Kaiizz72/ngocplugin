package com.craftvn.ngoc.listener;

import com.craftvn.ngoc.NgocPlugin;
import com.craftvn.ngoc.util.GemTag;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public class NgocUseListener implements Listener {

    private static NgocPlugin pluginRef;
    public NgocUseListener(NgocPlugin plugin){ pluginRef = plugin; }

    // Combat map: player <-> opponent (expire 5s)
    private final Map<UUID, UUID> lastHit = new HashMap<>();

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent e){
        if (!(e.getEntity() instanceof Player victim)) return;
        Player damager = null;
        if (e.getDamager() instanceof Player p) damager = p;
        else if (e.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) damager = p;
        if (damager == null) return;
        lastHit.put(damager.getUniqueId(), victim.getUniqueId());
        lastHit.put(victim.getUniqueId(), damager.getUniqueId());
        Bukkit.getScheduler().runTaskLater(pluginRef, () -> {
            lastHit.remove(damager.getUniqueId(), victim.getUniqueId());
            lastHit.remove(victim.getUniqueId(), damager.getUniqueId());
        }, 100L);
    }

    private String c(String s){ return ChatColor.translateAlternateColorCodes('&', s==null?"":s); }

    // ===== Purchase from PE Form =====
    public static void tryBuyGem(NgocPlugin plugin, Player p, String gemKey){
        NgocUseListener self = new NgocUseListener(plugin);
        if (self.consumeRequirements(p, gemKey)){
            ItemStack gem = self.createTaggedGem(gemKey);
            p.getInventory().addItem(gem);
            self.playGemSound(p, gemKey);
            p.sendMessage(self.c("&aĐã nhận &f" + plugin.getConfig().getString("gems."+gemKey+".name")));
        }
    }

    // ===== GUI PC click =====
    @EventHandler
    public void onInvClick(InventoryClickEvent e){
        if (!e.getView().getTitle().equals(c("&aShop Ngọc"))) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        ItemStack it = e.getCurrentItem();
        String gemKey = GemTag.readGem(pluginRef, it);
        if (gemKey == null) return;
        if (consumeRequirements(p, gemKey)){
            ItemStack gem = createTaggedGem(gemKey);
            p.getInventory().addItem(gem);
            playGemSound(p, gemKey);
            p.sendMessage(c("&aĐã nhận &f" + pluginRef.getConfig().getString("gems."+gemKey+".name")));
        }
    }
    @EventHandler public void onDrag(InventoryDragEvent e){
        if (e.getView().getTitle().equals(c("&aShop Ngọc"))) e.setCancelled(true);
    }

    // ===== Use gem =====
    @EventHandler
    public void onUse(PlayerInteractEvent e){
        ItemStack it = e.getItem();
        String key = GemTag.readGem(pluginRef, it);
        if (key == null) return;
        Player p = e.getPlayer();
        runGemSkill(p, key, it);
    }

    private ItemStack createTaggedGem(String key){
        Material mat = Material.matchMaterial(pluginRef.getConfig().getString("gems."+key+".material"));
        ItemStack item = new ItemStack(mat == null ? Material.PAPER : mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(c(pluginRef.getConfig().getString("gems."+key+".name")));
        List<String> lore = new ArrayList<>();
        for (String l : pluginRef.getConfig().getStringList("gems."+key+".lore")) lore.add(c(l));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return GemTag.tagGem(pluginRef, item, key);
    }

    private boolean consumeRequirements(Player p, String gemKey){
        int needExp = pluginRef.getConfig().getInt("gems."+gemKey+".exp", 0);
        List<String> list = pluginRef.getConfig().getStringList("gems."+gemKey+".items");
        Map<Material,Integer> req = new HashMap<>();
        for (String s : list){
            String[] sp = s.split(":"); if (sp.length<2) continue;
            Material m = Material.matchMaterial(sp[0]); int amt = Integer.parseInt(sp[1]);
            req.merge(m, amt, Integer::sum);
        }
        if (p.getLevel() < needExp){ p.sendMessage(c("&cBạn cần "+needExp+" levels!")); return false; }
        for (var e : req.entrySet()){
            if (!p.getInventory().containsAtLeast(new ItemStack(e.getKey()), e.getValue())){
                p.sendMessage(c("&cThiếu &e"+e.getValue()+" &f"+e.getKey().name())); return false;
            }
        }
        int old = p.getLevel();
        p.setLevel(old - needExp);
        Map<Integer, ItemStack> snapshot = new HashMap<>();
        for (int i=0;i<p.getInventory().getSize();i++){
            ItemStack cur = p.getInventory().getItem(i);
            if (cur!=null) snapshot.put(i, cur.clone());
        }
        Map<Material,Integer> remain = new HashMap<>(req);
        for (int i=0;i<p.getInventory().getSize();i++){
            ItemStack cur = p.getInventory().getItem(i); if (cur==null) continue;
            Integer need = remain.get(cur.getType()); if (need==null || need<=0) continue;
            int take = Math.min(need, cur.getAmount());
            cur.setAmount(cur.getAmount()-take);
            remain.put(cur.getType(), need-take);
        }
        boolean ok = remain.values().stream().allMatch(v -> v<=0);
        if (!ok){
            p.setLevel(old);
            snapshot.forEach((slot,it) -> p.getInventory().setItem(slot, it));
            p.sendMessage(c("&cKhông đủ vật phẩm!"));
            return false;
        }
        return true;
    }

    private void playGemSound(Player p, String gemKey){
        String k = pluginRef.getConfig().getString("gems."+gemKey+".sound");
        Sound s;
        try { s = Sound.valueOf(k); } catch (Exception ex){ s = Sound.ENTITY_EXPERIENCE_ORB_PICKUP; }
        p.getWorld().playSound(p.getLocation(), s, 1, 1);
    }

    // ===== Skills =====
    private void runGemSkill(Player p, String key, ItemStack used){
        switch (key){
            case "tocdo" -> { p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 15*20, 1)); consumeOne(used); }
            case "hoimau" -> { p.setHealth(Math.min(p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue(), p.getHealth()+20.0)); p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 10*20, 1)); consumeOne(used); }
            case "sinhluc" -> { tempHealth(p, 10.0, 10*20); consumeOne(used); }
            case "phunlua" -> { fireBurst(p, 3, 100, 4.0); consumeOne(used); }
            case "tonhen" -> { webs(p.getLocation(), 2, 100L); consumeOne(used); }
            case "longkinh" -> { glassSphere(p.getLocation(), 7, 400L); consumeOne(used); }
            case "tanghinh" -> { p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 15*20, 0)); consumeOne(used); }
            case "dichchuyen" -> { teleportNearest(p, 50); consumeOne(used); }
            case "samset" -> { lightningAoE(p, 5); consumeOne(used); }
            case "bomno" -> { p.getWorld().createExplosion(p.getLocation(), 4F, false, false, p); p.getWorld().spawnParticle(Particle.EXPLOSION_HUGE, p.getLocation(), 1); consumeOne(used); }
            case "hacdien" -> { blindnessAoE(p, 7, 10*20); consumeOne(used); }
            case "netherite" -> { p.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 8*20, 4)); consumeOne(used); }
            case "beacon" -> { beaconBuff(p, 30*20); consumeOne(used); }
        }
    }

    private void consumeOne(ItemStack it){ if (it==null) return; it.setAmount(it.getAmount()-1); }

    private void tempHealth(Player p, double add, long ticks){
        double base = p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue();
        p.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(base + add);
        Bukkit.getScheduler().runTaskLater(pluginRef, () -> {
            p.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(base);
            if (p.getHealth() > base) p.setHealth(base);
        }, ticks);
    }

    private void webs(Location loc, int r, long ticks){
        for (int x=-r;x<=r;x++){
            for (int z=-r;z<=r;z++){
                Location l = loc.clone().add(x, 0, z);
                if (l.getBlock().getType()==Material.AIR){
                    l.getBlock().setType(Material.COBWEB, false);
                    Bukkit.getScheduler().runTaskLater(pluginRef, () -> {
                        if (l.getBlock().getType()==Material.COBWEB) l.getBlock().setType(Material.AIR, false);
                    }, ticks);
                }
            }
        }
    }

    private void glassSphere(Location center, int r, long ticks){
        World w = center.getWorld();
        for (int x=-r;x<=r;x++){
            for (int y=-r;y<=r;y++){
                for (int z=-r;z<=r;z++){
                    double d = Math.sqrt(x*x+y*y+z*z);
                    if (d<=r && d>=r-1){
                        Location l = center.clone().add(x,y,z);
                        if (l.getBlock().getType()==Material.AIR){
                            l.getBlock().setType(Material.GLASS, false);
                            Bukkit.getScheduler().runTaskLater(pluginRef, () -> {
                                if (l.getBlock().getType()==Material.GLASS) l.getBlock().setType(Material.AIR, false);
                            }, ticks);
                        }
                    }
                }
            }
        }
    }

    private void fireBurst(Player p, int r, int fireTicks, double damage){
        for (var e : p.getNearbyEntities(r, r, r)){
            if (e instanceof LivingEntity le && !e.equals(p)){
                le.setFireTicks(fireTicks);
                le.damage(damage, p);
            }
        }
        p.getWorld().spawnParticle(Particle.FLAME, p.getLocation(), 100, 3, 1, 3, 0.1);
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1, 1);
    }

    private void lightningAoE(Player p, int r){
        for (var e : p.getNearbyEntities(r, r, r)){
            if (e instanceof LivingEntity le && !e.equals(p)){
                p.getWorld().strikeLightning(le.getLocation());
            }
        }
    }

    private void blindnessAoE(Player p, int r, int ticks){
        for (Player o : Bukkit.getOnlinePlayers()){
            if (o.equals(p)) continue;
            if (!o.getWorld().equals(p.getWorld())) continue;
            if (o.getLocation().distance(p.getLocation())<=r){
                o.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, ticks, 0));
                o.sendMessage(c("&5Bạn bị mù bởi Ngọc Hắc Diện!"));
            }
        }
    }

    private void beaconBuff(Player p, int ticks){
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, ticks, 1));
        p.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, ticks, 1));
        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, ticks, 1));
        p.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, ticks, 1));
        p.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, ticks, 1));
        p.getWorld().spawnParticle(Particle.BEACON_BEAM, p.getLocation(), 5, 2,2,2);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1, 1);
    }

    private Player nearestTarget(Player p, double radius){
        UUID fighting = lastHit.get(p.getUniqueId());
        Player best = null; double d2 = radius*radius;
        for (Player o : Bukkit.getOnlinePlayers()){
            if (o.equals(p)) continue;
            if (!o.getWorld().equals(p.getWorld())) continue;
            if (o.getGameMode()==GameMode.SPECTATOR) continue;
            if (fighting!=null && o.getUniqueId().equals(fighting)) continue;
            double dist = o.getLocation().distanceSquared(p.getLocation());
            if (dist < d2){ d2 = dist; best = o; }
        }
        return best;
    }

    private void teleportNearest(Player p, double radius){
        Player t = nearestTarget(p, radius);
        if (t==null){ p.sendMessage(c("&cKhông tìm thấy người chơi phù hợp!")); return; }
        p.teleport(t.getLocation().add(0,1,0));
        p.sendMessage(c("&dDịch chuyển tới &f"+t.getName()));
    }
}
