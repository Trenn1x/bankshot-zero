package game;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** The entire game simulation. No browser, graphics, audio, or platform dependencies. */
public final class Engine {
    public static final int WIDTH = 1120, HEIGHT = 700;
    public static final double LEFT = 40, RIGHT = 1080, TOP = 86, BOTTOM = 648;
    public String state = "TITLE";
    public double time, px = 560, py = 380, aimX = 760, aimY = 380;
    public double health = 100, maxHealth = 100, dashCooldown, shake;
    public double playerRadius = 14, invulnerable, dashTime, waveBanner, waveTimer;
    public int wave, score, kills, combo, enemiesRemaining, shotsFired, hitsTaken, enemyDeaths;
    public int bankShots, bestCombo;
    public double comboTimer;
    public final List<Enemy> enemies = new ArrayList<Enemy>();
    public final List<Bullet> bullets = new ArrayList<Bullet>();
    public final List<Particle> particles = new ArrayList<Particle>();
    public final List<Obstacle> obstacles = new ArrayList<Obstacle>();
    public String[] upgradeNames = { "", "", "" };
    public String[] upgradeDescriptions = { "", "", "" };
    public String[] upgradeColors = { "#66f3df", "#ff796e", "#b4a1ff" };

    public static final class Enemy {
        public double x, y, r, hp, maxHp, angle, telegraph, hitFlash, shootTimer;
        public double age, attackWindup;
        public int type, pattern;
        public Enemy() { }
        public Enemy(double x, double y, int type, double hp) {
            this.x = x; this.y = y; this.type = type; this.hp = this.maxHp = hp;
            this.r = type == 3 ? 44 : type == 2 ? 23 : type == 1 ? 17 : 14;
            this.telegraph = 0.85; this.shootTimer = 1.7;
        }
    }

    public static final class Bullet {
        public double x, y, vx, vy, r = 4, life = 3, damage = 20, age;
        public boolean hostile;
        public int bounces = 2, ricochets;
        public Bullet() { }
        public Bullet(double x, double y, double vx, double vy, boolean hostile) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy; this.hostile = hostile;
        }
    }

    public static final class Particle {
        public double x, y, vx, vy, r, life, maxLife;
        public String color;
        public Particle(double x, double y, double vx, double vy, double r, double life, String color) {
            this.x=x; this.y=y; this.vx=vx; this.vy=vy; this.r=r;
            this.life=this.maxLife=life; this.color=color;
        }
    }

    public static final class Obstacle {
        public double x, y, w, h;
        public Obstacle(double x, double y, double w, double h) {
            this.x=x; this.y=y; this.w=w; this.h=h;
        }
    }

    private static final String[] NAMES = {
        "RAPID CIRCUIT", "OVERCHARGE", "SPLIT VECTOR", "SECOND WIND",
        "AFTERIMAGE", "BANK ACCOUNT", "HYPERVELOCITY", "REPAIR LOOP"
    };
    private static final String[] DESCRIPTIONS = {
        "Fire 18% faster. Keep the pressure on.",
        "Projectiles deal 30% more damage.",
        "Add a projectile to every shot. Wider coverage.",
        "+25 maximum hull. Repair 40 hull now.",
        "Dash recharges 22% faster. Move 6% faster.",
        "+1 ricochet. Bank shots deal more bonus damage.",
        "Projectiles travel 18% faster. +10% damage.",
        "Restore 7 hull every 6 eliminations. Repair 20 now."
    };
    private final long seed;
    private Random random;
    private int pendingSpawns, spawnedThisWave, pellets = 1, ricochetLimit = 2, repairLoops;
    private final int[] upgradeIds = new int[3];
    private double spawnTimer, shootCooldown, fireInterval = 0.185, damage = 20;
    private double bulletSpeed = 760, moveSpeed = 228, dashRecharge = 1.85, bankBonus = 0.45;
    private double dashX, dashY, trailTimer;
    private boolean dashHeld;

    public Engine() { this(20261024L); }
    public Engine(long seed) {
        this.seed = seed; this.random = new Random(seed);
        createObstacles();
    }

    private void createObstacles() {
        obstacles.clear();
        obstacles.add(new Obstacle(340, 240, 110, 30));
        obstacles.add(new Obstacle(670, 240, 110, 30));
        obstacles.add(new Obstacle(340, 490, 110, 30));
        obstacles.add(new Obstacle(670, 490, 110, 30));
    }

    /** Restart restores the same seed, upgrade stats, input latches, and all counters. */
    public void start() {
        random = new Random(seed);
        state="PLAY"; time=0; px=560; py=380; aimX=760; aimY=380;
        health=maxHealth=100; dashCooldown=shake=invulnerable=dashTime=0;
        score=kills=combo=bestCombo=bankShots=shotsFired=hitsTaken=enemyDeaths=0;
        comboTimer=shootCooldown=trailTimer=waveTimer=0;
        pellets=1; ricochetLimit=2; repairLoops=0;
        fireInterval=0.185; damage=20; bulletSpeed=760; moveSpeed=228;
        dashRecharge=1.85; bankBonus=0.45; dashHeld=false; dashX=dashY=0;
        enemies.clear(); bullets.clear(); particles.clear(); createObstacles();
        for (int i=0; i<3; i++) { upgradeIds[i]=0; upgradeNames[i]=""; upgradeDescriptions[i]=""; }
        beginWave(1);
    }

    private void beginWave(int nextWave) {
        wave=nextWave; waveTimer=0; waveBanner=2.6; spawnedThisWave=0;
        pendingSpawns = wave == 8 ? 9 : 5 + wave*3;
        spawnTimer=1.3; bullets.clear();
        invulnerable=Math.max(invulnerable, 0.9);
        if (wave == 8) {
            Enemy boss = new Enemy(560, 156, 3, 1550);
            boss.telegraph=2.1; boss.shootTimer=1.1; enemies.add(boss);
        }
        enemiesRemaining=pendingSpawns+enemies.size();
    }

    public void togglePause() {
        if ("PLAY".equals(state)) state="PAUSE";
        else if ("PAUSE".equals(state)) state="PLAY";
    }

    /** A bounded fixed substep keeps collision behavior reliable through slow browser frames. */
    public void update(double dt, double moveX, double moveY, double targetX, double targetY,
                       boolean shooting, boolean dash) {
        if (!Double.isFinite(dt) || dt <= 0) return;
        if ("PAUSE".equals(state)) return;
        dt=Math.min(dt, 0.25);
        if (!"PLAY".equals(state)) {
            // Finishing particles remain alive on upgrade and end screens.
            updateParticles(dt); shake=Math.max(0, shake-dt*22);
            return;
        }
        aimX=Double.isFinite(targetX) ? targetX : px+1;
        aimY=Double.isFinite(targetY) ? targetY : py;
        if (!Double.isFinite(moveX) || !Double.isFinite(moveY)) { moveX=0; moveY=0; }
        double length=Math.sqrt(moveX*moveX+moveY*moveY);
        if (length>1) { moveX/=length; moveY/=length; }
        boolean dashPressed=dash && !dashHeld;
        dashHeld=dash;
        if (dashPressed && dashCooldown<=0) {
            double ax=moveX, ay=moveY, al=Math.sqrt(ax*ax+ay*ay);
            if (al < 0.01) { ax=aimX-px; ay=aimY-py; al=Math.sqrt(ax*ax+ay*ay); }
            if (al < 0.01) { ax=1; ay=0; al=1; }
            dashX=ax/al; dashY=ay/al; dashTime=0.17; invulnerable=0.26;
            dashCooldown=dashRecharge;
            burst(px,py,12,"#66f3df",160);
        }
        int steps=(int)Math.ceil(dt/(1.0/120));
        double step=dt/steps;
        for (int i=0; i<steps && "PLAY".equals(state); i++) tick(step,moveX,moveY,shooting);
    }

    private void tick(double dt, double mx, double my, boolean shooting) {
        time+=dt; waveTimer+=dt;
        waveBanner=Math.max(0,waveBanner-dt); shake=Math.max(0,shake-dt*22);
        dashCooldown=Math.max(0,dashCooldown-dt); invulnerable=Math.max(0,invulnerable-dt);
        shootCooldown-=dt; comboTimer=Math.max(0,comboTimer-dt);
        if (comboTimer==0) combo=0;
        boolean dashing=dashTime>0;
        if (dashing) dashTime=Math.max(0,dashTime-dt);
        double speed=dashing ? 860 : moveSpeed;
        movePlayer((dashing ? dashX : mx)*speed*dt,(dashing ? dashY : my)*speed*dt);
        trailTimer-=dt;
        if (dashing && trailTimer<=0) {
            particles.add(new Particle(px,py,0,0,12,0.25,"#66f3df")); trailTimer=0.025;
        }
        if (shooting && shootCooldown<=0) {
            firePlayer(); shootCooldown=fireInterval;
        } else if (!shooting) shootCooldown=Math.max(0,shootCooldown);

        spawnTimer-=dt;
        if (pendingSpawns>0 && spawnTimer<=0 && enemies.size()<9) {
            spawnEnemy(); pendingSpawns--;
            spawnTimer=wave==8 ? 2.4 : Math.max(0.8,1.3-wave*0.04);
        }
        updateEnemies(dt);
        updateBullets(dt);
        updateParticles(dt);
        enemiesRemaining=pendingSpawns+enemies.size();
        if (health<=0) {
            health=0; state="DEAD"; shake=10; burst(px,py,45,"#ff796e",270); return;
        }
        if (pendingSpawns==0 && enemies.isEmpty()) {
            bullets.clear(); comboTimer=0; combo=0;
            if (wave==8) {
                state="WIN"; score+=(int)Math.ceil(health)*30; burst(px,py,75,"#66f3df",300);
            } else {
                health=Math.min(maxHealth,health+12);
                generateUpgrades(); state="UPGRADE";
            }
        }
    }

    private void movePlayer(double dx, double dy) {
        px=clamp(px+dx,LEFT+playerRadius,RIGHT-playerRadius);
        for (Obstacle o:obstacles) if (circleRect(px,py,playerRadius,o)) {
            if (dx>0) px=o.x-playerRadius; else if (dx<0) px=o.x+o.w+playerRadius;
        }
        py=clamp(py+dy,TOP+playerRadius,BOTTOM-playerRadius);
        for (Obstacle o:obstacles) if (circleRect(px,py,playerRadius,o)) {
            if (dy>0) py=o.y-playerRadius; else if (dy<0) py=o.y+o.h+playerRadius;
        }
    }

    private void firePlayer() {
        double angle=Math.atan2(aimY-py,aimX-px);
        for (int i=0;i<pellets;i++) {
            double a=angle+(i-(pellets-1)*0.5)*0.16;
            Bullet b=new Bullet(px+Math.cos(a)*20,py+Math.sin(a)*20,
                                Math.cos(a)*bulletSpeed,Math.sin(a)*bulletSpeed,false);
            b.damage=damage; b.bounces=ricochetLimit; b.life=3.1; bullets.add(b);
        }
        shotsFired++;
        double a=angle;
        particles.add(new Particle(px+Math.cos(a)*23,py+Math.sin(a)*23,0,0,7,0.075,"#fff5cd"));
    }

    private void spawnEnemy() {
        double x=0,y=0;
        for (int attempt=0;attempt<20;attempt++) {
            int edge=random.nextInt(4);
            if (edge<2) { x=edge==0 ? LEFT+28 : RIGHT-28; y=TOP+28+random.nextDouble()*(BOTTOM-TOP-56); }
            else { x=LEFT+28+random.nextDouble()*(RIGHT-LEFT-56); y=edge==2 ? TOP+28 : BOTTOM-28; }
            if (distanceSquared(x,y,px,py)>260*260) break;
        }
        int type=0;
        if (wave>=2 && spawnedThisWave%3==1) type=1;
        if (wave>=3 && spawnedThisWave%5==4) type=2;
        if (wave==8) type=spawnedThisWave%3==2 ? 2 : 1;
        double hp=type==2 ? 92+wave*5 : type==1 ? 42+wave*3 : 28+wave*3;
        Enemy enemy=new Enemy(x,y,type,hp);
        enemy.shootTimer=1.8+random.nextDouble()*0.6;
        enemy.angle=Math.atan2(py-y,px-x);
        enemies.add(enemy); spawnedThisWave++;
    }

    private void updateEnemies(double dt) {
        for (Enemy e:enemies) {
            e.age+=dt; e.hitFlash=Math.max(0,e.hitFlash-dt);
            if (e.telegraph>0) { e.telegraph=Math.max(0,e.telegraph-dt); continue; }
            double dx=px-e.x,dy=py-e.y,dist=Math.max(1,Math.sqrt(dx*dx+dy*dy));
            e.angle=Math.atan2(dy,dx); e.shootTimer-=dt;
            e.attackWindup=e.type==0 ? 0 : clamp((0.48-e.shootTimer)/0.48,0,1);
            double nx=dx/dist,ny=dy/dist,speed,mx,my;
            if (e.type==0) {
                speed=90+wave*4.5; mx=nx*speed; my=ny*speed;
            } else if (e.type==1) {
                double forward=dist>315 ? 1 : dist<235 ? -0.65 : 0;
                speed=68+wave*2; double orbit=(e.age%8<4 ? 1 : -1)*0.4;
                mx=(nx*forward-ny*orbit)*speed; my=(ny*forward+nx*orbit)*speed;
            } else if (e.type==2) {
                speed=58+wave*2; mx=nx*speed; my=ny*speed;
            } else {
                double tx=560+Math.sin(e.age*0.48)*295,ty=320+Math.cos(e.age*0.36)*125;
                double vx=tx-e.x,vy=ty-e.y,vl=Math.max(1,Math.sqrt(vx*vx+vy*vy));
                mx=vx/vl*76; my=vy/vl*76;
            }
            // Local separation prevents unavoidable stacks without teleporting enemies.
            for (Enemy other:enemies) if (e!=other && other.telegraph<=0) {
                double sx=e.x-other.x,sy=e.y-other.y,d2=sx*sx+sy*sy;
                double radius=e.r+other.r+6;
                if (d2>0.01 && d2<radius*radius) {
                    double length=Math.sqrt(d2),push=(radius-length)*5;
                    mx+=sx/length*push; my+=sy/length*push;
                }
            }
            moveEnemy(e,mx*dt,my*dt);
            if (e.type>0 && e.shootTimer<=0) {
                if (e.type==1) { enemyShot(e,e.angle,230+wave*5,5); e.shootTimer=2.05-wave*0.065; }
                else if (e.type==2) {
                    for (int n=-1;n<=1;n++) enemyShot(e,e.angle+n*0.22,190+wave*3,6);
                    e.shootTimer=2.8;
                } else {
                    boolean furious=e.hp<e.maxHp*0.45;
                    if (e.pattern%2==0) {
                        int count=furious?16:12;
                        for (int n=0;n<count;n++) enemyShot(e,e.age*0.6+n*Math.PI*2/count,furious?220:180,6);
                    } else {
                        for (int n=-2;n<=2;n++) enemyShot(e,e.angle+n*0.19,furious?300:260,6);
                    }
                    e.pattern++; e.shootTimer=furious?1.15:1.6;
                }
            }
            if (distanceSquared(px,py,e.x,e.y)<square(playerRadius+e.r-3)) {
                hurt(e.type==3 ? 28 : e.type==2 ? 23 : 17);
            }
        }
    }

    private void moveEnemy(Enemy e,double dx,double dy) {
        e.x=clamp(e.x+dx,LEFT+e.r,RIGHT-e.r);
        for (Obstacle o:obstacles) if(circleRect(e.x,e.y,e.r,o)) {
            if(dx>0)e.x=o.x-e.r; else if(dx<0)e.x=o.x+o.w+e.r;
        }
        e.y=clamp(e.y+dy,TOP+e.r,BOTTOM-e.r);
        for (Obstacle o:obstacles) if(circleRect(e.x,e.y,e.r,o)) {
            if(dy>0)e.y=o.y-e.r; else if(dy<0)e.y=o.y+o.h+e.r;
        }
    }

    private void enemyShot(Enemy e,double angle,double speed,double radius) {
        Bullet b=new Bullet(e.x+Math.cos(angle)*(e.r+8),e.y+Math.sin(angle)*(e.r+8),
                            Math.cos(angle)*speed,Math.sin(angle)*speed,true);
        b.r=radius; b.life=5.8; b.bounces=0; b.damage=e.type==3 ? 12 : 10;
        bullets.add(b);
    }

    private void updateBullets(double dt) {
        for (int i=bullets.size()-1;i>=0;i--) {
            Bullet b=bullets.get(i); b.life-=dt; b.age+=dt;
            b.x+=b.vx*dt; b.y+=b.vy*dt;
            boolean collision=false;
            if(b.x-b.r<LEFT) { b.x=LEFT+b.r; b.vx=Math.abs(b.vx); collision=true; }
            else if(b.x+b.r>RIGHT) { b.x=RIGHT-b.r; b.vx=-Math.abs(b.vx); collision=true; }
            if(b.y-b.r<TOP) { b.y=TOP+b.r; b.vy=Math.abs(b.vy); collision=true; }
            else if(b.y+b.r>BOTTOM) { b.y=BOTTOM-b.r; b.vy=-Math.abs(b.vy); collision=true; }
            for (Obstacle o:obstacles) if(circleRect(b.x,b.y,b.r,o)) {
                double qx=clamp(b.x,o.x,o.x+o.w),qy=clamp(b.y,o.y,o.y+o.h);
                double nx=b.x-qx,ny=b.y-qy,length=Math.sqrt(nx*nx+ny*ny);
                if(length<0.0001) {
                    double left=Math.abs(b.x-o.x),right=Math.abs(o.x+o.w-b.x);
                    double top=Math.abs(b.y-o.y),bottom=Math.abs(o.y+o.h-b.y);
                    double nearest=Math.min(Math.min(left,right),Math.min(top,bottom));
                    if(nearest==left) { nx=-1;ny=0;qx=o.x; }
                    else if(nearest==right) { nx=1;ny=0;qx=o.x+o.w; }
                    else if(nearest==top) { nx=0;ny=-1;qy=o.y; }
                    else { nx=0;ny=1;qy=o.y+o.h; }
                } else { nx/=length;ny/=length; }
                b.x=qx+nx*(b.r+0.1); b.y=qy+ny*(b.r+0.1);
                double dot=b.vx*nx+b.vy*ny;
                if(dot<0) { b.vx-=2*dot*nx;b.vy-=2*dot*ny; }
                collision=true; break;
            }
            if(collision) {
                if(b.hostile || b.bounces<=0) b.life=0;
                else {
                    b.bounces--;b.ricochets++;b.damage*=1+bankBonus;
                    burst(b.x,b.y,4,"#66f3df",90);
                }
            }
            if(b.life>0 && b.hostile) {
                if(distanceSquared(b.x,b.y,px,py)<square(b.r+playerRadius-2)) {
                    hurt(b.damage); b.life=0;
                }
            } else if(b.life>0) {
                for(int j=enemies.size()-1;j>=0;j--) {
                    Enemy e=enemies.get(j);
                    if(e.telegraph>0 || distanceSquared(b.x,b.y,e.x,e.y)>=square(b.r+e.r)) continue;
                    e.hp-=b.damage; e.hitFlash=0.09;b.life=0;
                    burst(b.x,b.y,4,b.ricochets>0 ? "#66f3df" : "#fff5cd",100);
                    if(e.hp<=0) killEnemy(j,b.ricochets>0);
                    break;
                }
            }
            if(b.life<=0) bullets.remove(i);
        }
    }

    private void killEnemy(int index,boolean banked) {
        Enemy e=enemies.remove(index); kills++;enemyDeaths++;combo++;comboTimer=3.8;
        bestCombo=Math.max(bestCombo,combo);
        int multiplier=Math.min(5,1+combo/5);
        score+=(e.type==3?5000:e.type==2?250:e.type==1?150:100)*multiplier*(banked?2:1);
        if(banked) bankShots++;
        if(repairLoops>0 && kills%6==0) health=Math.min(maxHealth,health+7*repairLoops);
        shake=Math.max(shake,e.type==3?12:2.5);
        burst(e.x,e.y,e.type==3?65:15,e.type==3?"#b4a1ff":banked?"#66f3df":"#ff796e",e.type==3?290:165);
        if(e.type==3) {
            // Killing the final boss ends its entire pattern and dissolves its escorts.
            for(Enemy escort:enemies) burst(escort.x,escort.y,10,"#66f3df",170);
            enemyDeaths+=enemies.size();kills+=enemies.size();enemies.clear();pendingSpawns=0;
            for(Bullet b:bullets) if(b.hostile)b.life=0;
        }
    }

    private void hurt(double amount) {
        if(invulnerable>0 || dashTime>0 || health<=0) return;
        health=Math.max(0,health-amount);hitsTaken++;combo=0;comboTimer=0;
        invulnerable=0.8;shake=8;burst(px,py,19,"#ff796e",210);
    }

    private void burst(double x,double y,int count,String color,double speed) {
        // Cap cosmetics, never simulation objects, on slower machines.
        for(int i=0;i<count && particles.size()<420;i++) {
            double a=random.nextDouble()*Math.PI*2,v=speed*(0.2+random.nextDouble()*0.8);
            particles.add(new Particle(x,y,Math.cos(a)*v,Math.sin(a)*v,
                                      1.5+random.nextDouble()*3,0.2+random.nextDouble()*0.4,color));
        }
    }

    private void updateParticles(double dt) {
        for(int i=particles.size()-1;i>=0;i--) {
            Particle p=particles.get(i);p.life-=dt;
            p.x+=p.vx*dt;p.y+=p.vy*dt;
            double drag=Math.max(0,1-dt*4);p.vx*=drag;p.vy*=drag;
            if(p.life<=0)particles.remove(i);
        }
    }

    private void generateUpgrades() {
        for(int i=0;i<3;i++) {
            int id;
            boolean duplicate;
            do {
                id=random.nextInt(NAMES.length);duplicate=false;
                for(int j=0;j<i;j++)if(upgradeIds[j]==id)duplicate=true;
                if(id==2 && pellets>=4)duplicate=true;
                if(id==7 && repairLoops>=2)duplicate=true;
            } while(duplicate);
            upgradeIds[i]=id;upgradeNames[i]=NAMES[id];upgradeDescriptions[i]=DESCRIPTIONS[id];
        }
    }

    public void chooseUpgrade(int index) {
        if(!"UPGRADE".equals(state) || index<0 || index>=3)return;
        switch(upgradeIds[index]) {
            case 0: fireInterval*=0.82;break;
            case 1: damage*=1.30;break;
            case 2: pellets++;break;
            case 3: maxHealth+=25;health=Math.min(maxHealth,health+40);break;
            case 4: dashRecharge*=0.78;moveSpeed*=1.06;break;
            case 5: ricochetLimit++;bankBonus+=0.2;break;
            case 6: bulletSpeed*=1.18;damage*=1.10;break;
            case 7: repairLoops++;health=Math.min(maxHealth,health+20);break;
            default: break;
        }
        state="PLAY";dashHeld=false;dashCooldown=0;shootCooldown=0;
        beginWave(wave+1);
    }

    private static boolean circleRect(double x,double y,double r,Obstacle o) {
        return distanceSquared(x,y,clamp(x,o.x,o.x+o.w),clamp(y,o.y,o.y+o.h)) < r*r-0.0001;
    }
    private static double distanceSquared(double x,double y,double xx,double yy) {
        return square(x-xx)+square(y-yy);
    }
    private static double square(double value) {return value*value;}
    private static double clamp(double value,double min,double max) {return Math.max(min,Math.min(max,value));}
}
