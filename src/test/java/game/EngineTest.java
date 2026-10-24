package game;

/** Run with java game.EngineTest. Tests the shipped simulation without a browser. */
public final class EngineTest {
    private static int assertions;

    public static void main(String[] args) {
        playerMovementAndCover();
        ricochetsAndHostileCover();
        damageDashAndPause();
        scoringAndBankShot();
        allWavesUpgradesAndBoss();
        deterministicRestart();
        slowFrameAndInvalidInput();
        System.out.println("EngineTest: " + assertions + " assertions passed.");
    }

    private static Engine fresh() {Engine e=new Engine(17);e.start();return e;}
    private static void step(Engine e,double dt) {e.update(dt,0,0,900,380,false,false);}

    private static void playerMovementAndCover() {
        Engine e=fresh();
        double x=e.px,y=e.py;
        e.update(0.1,1,1,900,380,false,false);
        near(Math.hypot(e.px-x,e.py-y),22.8,0.01,"diagonal input is normalized");
        e.px=300;e.py=255;
        for(int i=0;i<50;i++)e.update(0.02,1,0,900,380,false,false);
        check(e.px<=326.001,"player cannot cross cover");
        e.px=70;e.py=120;
        for(int i=0;i<50;i++)e.update(0.02,-1,-1,900,380,false,false);
        check(e.px>=Engine.LEFT+e.playerRadius,"left boundary contains player");
        check(e.py>=Engine.TOP+e.playerRadius,"top boundary contains player");
    }

    private static void ricochetsAndHostileCover() {
        Engine e=fresh();
        Engine.Bullet b=new Engine.Bullet(Engine.RIGHT-5,380,760,0,false);
        e.bullets.add(b);step(e,0.02);
        check(b.vx<0 && b.ricochets==1 && b.bounces==1,"wall reflects player shot exactly once");
        check(b.damage>20,"bank shot increases projectile damage");
        check(b.x<=Engine.RIGHT-b.r,"reflected bullet remains inside arena");
        e.bullets.clear();
        b=new Engine.Bullet(320,255,760,0,false);e.bullets.add(b);step(e,0.05);
        check(b.vx<0 && b.ricochets==1,"geometric cover reflects shots");
        e.bullets.clear();
        b=new Engine.Bullet(320,255,760,0,true);e.bullets.add(b);step(e,0.05);
        check(e.bullets.isEmpty(),"cover blocks hostile fire");
        b=new Engine.Bullet(Engine.RIGHT-5,380,760,0,false);b.bounces=0;e.bullets.add(b);step(e,0.02);
        check(e.bullets.isEmpty(),"spent ricochet budget destroys bullet");
    }

    private static void damageDashAndPause() {
        Engine e=fresh();e.invulnerable=0;
        e.bullets.add(new Engine.Bullet(e.px,e.py,0,0,true));step(e,0.01);
        check(e.health==80 && e.hitsTaken==1,"hostile projectile damages hull");
        e.bullets.add(new Engine.Bullet(e.px,e.py,0,0,true));step(e,0.01);
        check(e.health==80,"post-hit immunity prevents stacked damage");
        e.invulnerable=0;
        double x=e.px;
        e.update(0.05,1,0,900,380,false,true);
        check(e.px-x>35 && e.dashCooldown>1,"dash moves quickly and starts cooldown");
        e.bullets.add(new Engine.Bullet(e.px,e.py,0,0,true));
        e.update(0.01,0,0,900,380,false,true);
        check(e.health==80,"dash grants immunity");
        // Keeping the key held cannot trigger a second dash after its cooldown expires.
        for(int i=0;i<120;i++)e.update(0.02,0,0,900,380,false,true);
        check(e.dashCooldown==0 && e.dashTime==0,"dash requires a new press");
        e.togglePause();double time=e.time;double px=e.px;
        e.update(0.2,-1,0,800,300,true,true);
        check("PAUSE".equals(e.state) && e.time==time && e.px==px,"pause freezes simulation");
        e.togglePause();check("PLAY".equals(e.state),"pause resumes");
        e.health=5;e.invulnerable=0;
        e.bullets.add(new Engine.Bullet(e.px,e.py,0,0,true));step(e,0.01);
        check("DEAD".equals(e.state) && e.health==0,"fatal damage enters game over");
    }

    private static void scoringAndBankShot() {
        Engine e=fresh();
        Engine.Enemy enemy=new Engine.Enemy(800,380,0,1);enemy.telegraph=0;e.enemies.add(enemy);
        Engine.Bullet b=new Engine.Bullet(800,380,0,0,false);b.ricochets=1;e.bullets.add(b);
        step(e,0.01);
        check(e.kills==1 && e.enemyDeaths==1,"projectile kills count once");
        check(e.bankShots==1 && e.score==200,"bank-shot kill awards double score");
        check(e.combo==1 && e.comboTimer>3,"kill starts combo window");
        for(int i=0;i<20;i++)step(e,0.2);
        check(e.combo==0,"combo expires without new kills");
    }

    private static void allWavesUpgradesAndBoss() {
        Engine e=fresh();e.maxHealth=e.health=100000;
        for(int wave=1;wave<=7;wave++) {
            drainWave(e);
            check("UPGRADE".equals(e.state) && e.wave==wave,"cleared wave offers upgrade " + wave);
            check(!e.upgradeNames[0].isEmpty() && !e.upgradeDescriptions[2].isEmpty(),"upgrade cards populated");
            check(!e.upgradeNames[0].equals(e.upgradeNames[1]) && !e.upgradeNames[1].equals(e.upgradeNames[2])
                    && !e.upgradeNames[0].equals(e.upgradeNames[2]),"upgrade choices are distinct");
            e.chooseUpgrade(-1);check("UPGRADE".equals(e.state),"invalid selection is ignored");
            e.chooseUpgrade(wave%3);
            check("PLAY".equals(e.state) && e.wave==wave+1,"upgrade starts next wave");
        }
        Engine.Enemy boss=null;
        for(Engine.Enemy enemy:e.enemies)if(enemy.type==3)boss=enemy;
        check(boss!=null && boss.hp>1000,"wave eight spawns final boss");
        // The boss kill removes escorts and all pending waves, then reaches the win state.
        boss.telegraph=0;boss.hp=1;
        Engine.Bullet b=new Engine.Bullet(boss.x,boss.y,0,0,false);e.bullets.add(b);
        step(e,0.01);
        check("WIN".equals(e.state) && e.enemiesRemaining==0,"defeating boss wins run");
        check(e.bullets.isEmpty(),"victory clears projectiles");
    }

    private static void drainWave(Engine e) {
        for(int i=0;i<500 && "PLAY".equals(e.state);i++) {
            e.enemies.clear();e.bullets.clear();step(e,0.25);
        }
    }

    private static void deterministicRestart() {
        Engine e=fresh();
        for(int i=0;i<20;i++)e.update(0.1,0,0,800,300,true,false);
        double enemyX=e.enemies.get(0).x,enemyY=e.enemies.get(0).y;
        int count=e.bullets.size();
        e.start();
        check(e.health==100 && e.maxHealth==100 && e.wave==1,"restart restores hull and wave");
        check(e.score==0 && e.shotsFired==0 && e.kills==0 && e.combo==0,"restart resets counters");
        check(e.bullets.isEmpty() && e.enemies.isEmpty() && e.particles.isEmpty(),"restart clears objects");
        for(int i=0;i<20;i++)e.update(0.1,0,0,800,300,true,false);
        near(e.enemies.get(0).x,enemyX,0.00001,"seeded restart reproduces enemy x");
        near(e.enemies.get(0).y,enemyY,0.00001,"seeded restart reproduces enemy y");
        check(e.bullets.size()==count,"seeded restart reproduces projectiles");
    }

    private static void slowFrameAndInvalidInput() {
        Engine e=fresh();e.px=300;e.py=255;
        e.update(5,1,0,800,300,true,false);
        check(e.time<=0.250001 && e.px<=326.001,"slow frame is bounded and cannot tunnel through cover");
        double time=e.time;
        e.update(Double.NaN,1,0,0,0,true,false);
        check(e.time==time,"invalid timestep is ignored");
        e.update(0.02,Double.NaN,1,Double.NaN,Double.NaN,false,false);
        check(Double.isFinite(e.px) && Double.isFinite(e.py) && Double.isFinite(e.aimX),"invalid input cannot poison simulation");
        Engine.Bullet b=new Engine.Bullet(320,255,100000,0,false);
        // Real upgraded bullets remain below 2500 px/s; a normal 0.25 s frame uses 30 collision steps.
        b.vx=1600;e.bullets.clear();e.bullets.add(b);step(e,0.25);
        check(b.ricochets>0,"fast upgraded projectiles collide during slow frames");
    }

    private static void near(double actual,double expected,double tolerance,String message) {
        check(Math.abs(actual-expected)<=tolerance,message + " (" + actual + " vs " + expected + ")");
    }
    private static void check(boolean value,String message) {
        assertions++;if(!value)throw new AssertionError(message);
    }
}
