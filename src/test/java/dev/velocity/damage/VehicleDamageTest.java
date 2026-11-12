package dev.velocity.damage;

import dev.velocity.damage.VehicleDamage.Breakable;
import dev.velocity.damage.VehicleDamage.ImpactResult;
import dev.velocity.damage.VehicleDamage.Profile;
import dev.velocity.damage.VehicleDamage.Zone;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VehicleDamageTest {
    private static final Profile SPORT = new Profile(1f, Zone.FRONT, 2.2f, 0.96f, List.of(
            new Breakable("front", Zone.FRONT, 70f), new Breakable("hood", Zone.FRONT, 30f), new Breakable("rear", Zone.REAR, 70f)));
    private static final Profile MONSTER = new Profile(4f, Zone.FRONT, 2.6f, 1.3f, SPORT.breakables());

    private static ImpactResult front(VehicleDamage d, Profile p, float kmh) {
        return d.applyImpact(p, 0f, 0.5f, p.halfLength(), 0, 0, -1, kmh / 3.6f);
    }

    @Test
    void parkingBumpDoesNothing() {
        VehicleDamage d = new VehicleDamage();
        assertFalse(front(d, SPORT, 5).any());
        assertTrue(d.pristine());
    }

    @Test
    void lowSpeedCrashDentsButKeepsParts() {
        VehicleDamage d = new VehicleDamage();
        ImpactResult r = front(d, SPORT, 30);
        assertTrue(r.dentDepth() > 0.03f && r.dentDepth() < 0.2f, "profundidade " + r.dentDepth());
        assertEquals(1, d.dents().size());
        assertTrue(d.detached().isEmpty());
        assertEquals(100f, d.engineHealth());
        assertEquals(0f, r.occupantDamage());
    }

    @Test
    void mediumCrashDropsBumperAndHurtsEngine() {
        VehicleDamage d = new VehicleDamage();
        ImpactResult r = front(d, SPORT, 60);
        assertTrue(r.detachedNow().contains("front"), "para-choque deveria soltar: " + d.zoneHealth(Zone.FRONT));
        assertTrue(d.engineHealth() < 100f && d.engineHealth() > 25f, "motor " + d.engineHealth());
        assertFalse(d.burning());
        assertTrue(r.occupantDamage() > 0);
    }

    @Test
    void hardCrashIgnitesThenExplodes() {
        VehicleDamage d = new VehicleDamage();
        ImpactResult r = front(d, SPORT, 100);
        assertTrue(r.ignited() && d.burning(), "motor " + d.engineHealth());
        assertEquals(0f, r.instantExplosionPower());
        float power = 0;
        int ticks = 0;
        while (power == 0 && ticks < 400) { power = d.tickBurn(0.5f); ticks++; }
        assertTrue(ticks >= 60, "tempo para escapar: " + ticks + " ticks");
        assertTrue(power >= 2.5f && power <= 4f);
    }

    @Test
    void extremeCrashExplodesInstantlyAndScalesWithSpeed() {
        float p140 = front(new VehicleDamage(), SPORT, 140).instantExplosionPower();
        float p220 = front(new VehicleDamage(), SPORT, 220).instantExplosionPower();
        assertTrue(p140 >= 2f, "140 km/h deveria explodir: " + p140);
        assertTrue(p220 > p140 && p220 <= 5f);
    }

    @Test
    void monsterTruckShrugsOffWhatBreaksASportsCar() {
        VehicleDamage sport = new VehicleDamage(), monster = new VehicleDamage();
        front(sport, SPORT, 60);
        ImpactResult r = front(monster, MONSTER, 60);
        assertTrue(monster.detached().isEmpty());
        assertEquals(100f, monster.engineHealth(), 15f);
        assertTrue(r.dentDepth() < sport.dents().get(0).depth() / 2f);
        assertEquals(0f, front(new VehicleDamage(), MONSTER, 140).instantExplosionPower());
    }

    @Test
    void repeatedHitsInSamePlaceDeepenOneDent() {
        VehicleDamage d = new VehicleDamage();
        front(d, SPORT, 25);
        float first = d.dents().get(0).depth();
        front(d, SPORT, 25);
        assertEquals(1, d.dents().size());
        assertTrue(d.dents().get(0).depth() > first);
    }

    @Test
    void sideImpactDoesNotHurtFrontEngine() {
        VehicleDamage d = new VehicleDamage();
        d.applyImpact(SPORT, 0.96f, 0.5f, 0f, -1, 0, 0, 20f);
        assertEquals(Zone.LEFT, VehicleDamage.zoneOf(SPORT, 0.96f, 0f));
        assertEquals(100f, d.engineHealth());
        assertTrue(d.zoneHealth(Zone.LEFT) < 100f);
    }

    @Test
    void encodeAndLoadRoundTrip() {
        VehicleDamage d = new VehicleDamage();
        front(d, SPORT, 60);
        VehicleDamage copy = new VehicleDamage();
        copy.load(d.encodeDents(), d.zoneHealthArray(), d.engineHealth(), d.detached(), d.burnTicks(), d.wrecked());
        assertEquals(d.dents(), copy.dents());
        assertEquals(d.detached(), copy.detached());
        assertEquals(d.engineHealth(), copy.engineHealth());
    }

    @Test
    void wreckAndRepair() {
        VehicleDamage d = new VehicleDamage();
        d.wreck(SPORT, 42L);
        assertTrue(d.wrecked() && !d.engineUsable() && d.powerFactor() == 0f);
        assertTrue(d.detached().containsAll(List.of("front", "hood", "rear")));
        d.repairAll();
        assertTrue(d.pristine());
    }
}
