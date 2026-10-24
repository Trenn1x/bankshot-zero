package game;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/** Java renderer and input controller. JSBody methods are browser API adapters only. */
public final class WebGame {
    private static final String INK="#091b22", PANEL="#102a32", CREAM="#ecf0dc", MUTED="#6f9297", CORAL="#ff7955", MINT="#bbf7ce";
    private static final Engine engine=new Engine();
    private static double last=-1, clock, accumulator, touchAimX=1, touchAimY;
    private static int previousShots,previousDeaths,previousHits,best;
    private static boolean dashRequested, muted;
    private static final TouchStick moveStick=new TouchStick(), aimStick=new TouchStick();
    private static String previousState="";
    private static final Frame FRAME=WebGame::frame;
    @JSFunctor interface Frame extends JSObject {void call(double timestamp);}
    @JSFunctor interface Action extends JSObject {void call(String value);}
    @JSFunctor interface StickEvent extends JSObject {void call(String kind,double dx,double dy,double radius);}

    public static void main(String[] args) {
        boot(WebGame::action,WebGame::stickEvent);
        bind("start",v->action("Enter"));bind("restart",v->action("KeyR"));
        bind("resume",v->action("KeyP"));bind("pause-button",v->action("KeyP"));
        bind("sound",v->action("KeyM"));bindDash(WebGame::action);
        bind("fullscreen",v->fullscreen());
        for(int i=0;i<3;i++){final int index=i;bind("upgrade-"+i,v->choose(index));}
        best=loadBest(); muted=loadMute(); setText("sound",muted?"SOUND OFF":"SOUND ON");
        show("loader",false);syncState();request(FRAME);
    }
    private static void action(String value) {
        switch(value){
            case "Enter": if(engine.state.equals("TITLE"))start();break;
            case "KeyR":if(engine.state.equals("DEAD")||engine.state.equals("WIN"))start();break;
            case "Space":dashRequested=true;break;
            case "KeyP":case "Escape":engine.togglePause();break;
            case "blur":if(engine.state.equals("PLAY"))engine.togglePause();break;
            case "Digit1":choose(0);break;case "Digit2":choose(1);break;case "Digit3":choose(2);break;
            case "KeyM":muted=!muted;saveMute(muted);setText("sound",muted?"SOUND OFF":"SOUND ON");if(!muted)tone(3);break;
            default:break;
        }
        syncState();
    }
    private static void start(){engine.start();touchAimX=1;touchAimY=0;previousShots=previousDeaths=previousHits=0;accumulator=0;dashRequested=false;focus();if(!muted)tone(3);}
    private static void choose(int i){if(engine.state.equals("UPGRADE")){engine.chooseUpgrade(i);focus();if(!muted)tone(3);syncState();}}
    private static void frame(double now){
        double dt=last<0?0:Math.min(.06,(now-last)/1000);last=now;clock+=dt;
        if(engine.state.equals("PLAY")){
            accumulator+=dt;
            while(accumulator>=1.0/120){
                double mx=moveStick.worldX(portrait())+(key("KeyD")||key("ArrowRight")?1:0)-(key("KeyA")||key("ArrowLeft")?1:0);
                double my=moveStick.worldY(portrait())+(key("KeyS")||key("ArrowDown")?1:0)-(key("KeyW")||key("ArrowUp")?1:0);
                boolean touchFire=aimStick.active() && (Math.abs(aimStick.x())+Math.abs(aimStick.y())>0.001);
                if(touchFire){double length=Math.hypot(aimStick.x(),aimStick.y());touchAimX=aimStick.worldX(portrait())/length;touchAimY=aimStick.worldY(portrait())/length;}
                double tx=usingTouch()?engine.px+touchAimX*600:mouseX();
                double ty=usingTouch()?engine.py+touchAimY*600:mouseY();
                engine.update(1.0/120,mx,my,tx,ty,firing()||touchFire,dashRequested);
                dashRequested=false;accumulator-=1.0/120;
                if(!engine.state.equals("PLAY")){accumulator=0;break;}
            }
            if(!muted){if(engine.hitsTaken>previousHits)tone(2);else if(engine.enemyDeaths>previousDeaths)tone(1);else if(engine.shotsFired>previousShots)tone(0);}
            previousShots=engine.shotsFired;previousHits=engine.hitsTaken;previousDeaths=engine.enemyDeaths;
        }else accumulator=0;
        syncState();render();request(FRAME);
    }
    private static void syncState(){
        if(!previousState.equals(engine.state)){
            resetInput();dashRequested=false;
            String[] names={"title","upgrade","pause","result"};for(String n:names)show(n,false);
            switch(engine.state){
                case "TITLE":show("title",true);break;
                case "UPGRADE":
                    for(int i=0;i<3;i++){setText("up-name-"+i,engine.upgradeNames[i]);setText("up-desc-"+i,engine.upgradeDescriptions[i]);}
                    show("upgrade",true);break;
                case "PAUSE":show("pause",true);break;
                case "DEAD":case "WIN":
                    boolean won=engine.state.equals("WIN");boolean record=engine.score>best;
                    if(record){best=engine.score;saveBest(best);}
                    setText("result-label",won?"All eight sectors secured":"Signal lost / sector "+engine.wave);
                    setText("result-title",won?"ZERO LEFT STANDING.":"GOOD RUN. GO AGAIN.");
                    setText("result-score",pad(engine.score,6));
                    setText("result-detail",engine.kills+" targets down · "+(int)engine.time+" seconds in the arena");
                    setText("result-best",(record?"NEW PERSONAL BEST / ":"PERSONAL BEST / ")+pad(best,6));
                    show("result",true);if(!muted)tone(won?3:2);break;
                default:break;
            }
            previousState=engine.state;
        }
        setText("wave",pad(Math.max(1,engine.wave),2)+" / 08");
        String hp=(int)Math.ceil(engine.health)+" / "+(int)engine.maxHealth;
        setText("health",hp);setText("score",pad(engine.score,6));
        setText("dash",engine.dashCooldown<=0?"READY ↗":decimal(engine.dashCooldown)+"s");
        dashButton(engine.dashCooldown,engine.state.equals("PLAY"));
        telemetry(engine.state,engine.wave,engine.health,engine.score,engine.px,engine.py,engine.shotsFired,engine.kills,engine.bullets.size(),engine.enemies.size(),engine.aimX,engine.aimY);
    }
    private static String pad(int n,int count){String s=""+n;while(s.length()<count)s="0"+s;return s;}
    private static String decimal(double n){int v=(int)Math.ceil(n*10);return (v/10)+"."+(v%10);}
    private static void render(){
        boolean portraitView=portrait();
        clear();fill(0,0,1120,700,"#0b2027");
        // A subtle technical grid and arena survey marks keep projectiles legible.
        for(int x=0;x<1120;x+=40)line(x,0,x,700,"#132e36",1);
        for(int y=0;y<700;y+=40)line(0,y,1120,y,"#132e36",1);
        rect(40,86,1040,562,"#284851",2);
        line(40,86,220,86,CORAL,3);line(900,648,1080,648,CORAL,3);
        for(int x=80;x<1080;x+=80){line(x,80,x,86,MUTED,1);line(x,648,x,654,MUTED,1);}
        for(int y=126;y<648;y+=80){line(34,y,40,y,MUTED,1);line(1080,y,1086,y,MUTED,1);}
        if(!portraitView)text("BS—00 / CONTAINMENT ARENA",40,46,10,MUTED,"left",false);
        if(!portraitView)text(engine.state.equals("TITLE")?"LIVE SIMULATION":engine.wave==8?"WARNING / SENTINEL ACTIVE":"SECTOR "+pad(engine.wave,2)+" / LIVE",1080,46,10,engine.wave==8?CORAL:MUTED,"right",false);
        circle(560,367,115,"#23424a",1,false);circle(560,367,122,"#17343c",1,false);
        line(540,367,580,367,"#284851",1);line(560,347,560,387,"#284851",1);
        for(Engine.Obstacle o:engine.obstacles){
            fill(o.x+5,o.y+6,o.w,o.h,"#06151b");fill(o.x,o.y,o.w,o.h,"#19343d");
            rect(o.x,o.y,o.w,o.h,"#507078",1);
            line(o.x+7,o.y+7,o.x+o.w-7,o.y+7,CORAL,2);
            for(double x=o.x+9;x<o.x+o.w-5;x+=14)line(x,o.y+o.h-8,x+5,o.y+o.h-8,"#36545b",2);
        }
        if(engine.state.equals("TITLE")){attract();return;}
        double sx=0,sy=0;
        if(engine.shake>0&&!reduced()){sx=Math.sin(clock*91)*engine.shake;sy=Math.cos(clock*83)*engine.shake;}
        translate(sx,sy);
        for(Engine.Particle p:engine.particles){alpha(Math.max(0,p.life/p.maxLife));circle(p.x,p.y,p.r,p.color,1,true);}alpha(1);
        for(Engine.Bullet b:engine.bullets){
            String color=b.hostile?CORAL:b.ricochets>0?MINT:"#ffe7aa";
            double speed=Math.sqrt(b.vx*b.vx+b.vy*b.vy);
            if(speed>0)line(b.x-b.vx/speed*15,b.y-b.vy/speed*15,b.x,b.y,color,b.r*1.3);
            glow(b.x,b.y,b.r+9,color,.10);circle(b.x,b.y,b.r,color,1,true);
            circle(b.x,b.y,Math.max(1,b.r*.42),CREAM,1,true);
        }
        for(Engine.Enemy e:engine.enemies)enemy(e.x,e.y,e.r,e.angle,e.type,e.hp/e.maxHp,Math.max(e.telegraph,e.attackWindup),e.hitFlash);
        // Player silhouette: a mint core, mechanical outriggers, and a directional barrel.
        if(engine.invulnerable<=0||((int)(clock*18))%2==0){
            double a=Math.atan2(engine.aimY-engine.py,engine.aimX-engine.px);
            if(engine.dashTime>0){glow(engine.px,engine.py,44,MINT,.16);circle(engine.px,engine.py,26,MINT,1,false);}
            glow(engine.px,engine.py,30,MINT,.08);
            circle(engine.px,engine.py,16,"#1d393f",2,true);circle(engine.px,engine.py,16,MINT,2,false);
            line(engine.px+Math.cos(a)*8,engine.py+Math.sin(a)*8,engine.px+Math.cos(a)*26,engine.py+Math.sin(a)*26,MINT,7);
            polygon(new double[]{engine.px,engine.py-8,engine.px+7,engine.py+5,engine.px-7,engine.py+5},MINT,true,1);
            circle(engine.px,engine.py,22,"#bbf7ce33",1,false);
        }
        double ax=mouseX(),ay=mouseY();
        if(usingTouch()){double aimAngle=Math.atan2(engine.aimY-engine.py,engine.aimX-engine.px);ax=engine.px+Math.cos(aimAngle)*90;ay=engine.py+Math.sin(aimAngle)*90;}
        if((!usingTouch()||aimStick.active())&&ax>=40&&ax<=1080&&ay>=86&&ay<=648){circle(ax,ay,9,"#ecf0dc99",1,false);line(ax-15,ay,ax-6,ay,CREAM,1);line(ax+6,ay,ax+15,ay,CREAM,1);line(ax,ay-15,ax,ay-6,CREAM,1);line(ax,ay+6,ax,ay+15,CREAM,1);}
        translate(-sx,-sy);
        if(!portraitView && engine.combo>1){text("×"+engine.combo+" COMBO",66,119,12,MINT,"left",true);fill(66,128,Math.min(140,engine.comboTimer*45),2,MINT);}
        if(!portraitView)text("TARGETS / "+engine.enemiesRemaining,1054,623,10,MUTED,"right",false);
        if(!portraitView)text("RICOCHETS HIT HARDER",66,623,9,MUTED,"left",false);
        if(engine.waveBanner>0&&engine.state.equals("PLAY")){
            alpha(Math.min(1,engine.waveBanner));
            if(portraitView)screenPanel();else fill(375,300,370,104,"#091b22ed");
            uprightText(engine.wave==8?"FINAL SECTOR":"SECTOR "+pad(engine.wave,2),560,347,32,CREAM,true);
            uprightText(engine.wave==8?"THE SENTINEL":"KEEP MOVING. FIND YOUR ANGLE.",560,376,10,CORAL,false);alpha(1);
        }
        if(!portraitView)text("EXPERIMENT 001",40,679,9,MUTED,"left",false);
        if(!portraitView)text("BANKSHOT // ZERO",1080,679,9,MUTED,"right",false);
    }
    private static void attract(){
        // Animated bank-shot diagram; the actual game uses the same geometry.
        double[] path={668,480,1038,174,760,116,651,255,1014,551,783,612};
        for(int i=0;i<path.length-2;i+=2)line(path[i],path[i+1],path[i+2],path[i+3],"#ff795566",2);
        for(int i=2;i<path.length-2;i+=2){circle(path[i],path[i+1],11,CORAL,1,false);circle(path[i],path[i+1],3,CORAL,1,true);}
        for(int j=0;j<5;j++){
            double travel=(clock*.32+j*.2)%1*(path.length/2-1);int segment=(int)travel;double t=travel-segment;
            double x=path[segment*2]*(1-t)+path[segment*2+2]*t,y=path[segment*2+1]*(1-t)+path[segment*2+3]*t;
            glow(x,y,16,CORAL,.16);circle(x,y,4,CREAM,1,true);
        }
        enemy(861,371,35,-clock*.3,3,1,0,0);enemy(724,202,17,clock*.4,1,1,0,0);enemy(969,481,19,-clock*.4,0,1,0,0);
        circle(668,480,22,MINT,2,false);polygon(new double[]{668,467,679,488,657,488},MINT,true,1);
        if(!portrait())text("REFLECTION STUDY / 01",856,655,9,MUTED,"center",false);
    }
    private static void enemy(double x,double y,double r,double a,int type,double hp,double warning,double flash){
        String col=type==1?"#ffd089":type==2?"#b9a2e9":CORAL;
        if(flash>0)col=CREAM;
        if(warning>0){circle(x,y,r+8+Math.sin(clock*20)*3,col,2,false);line(x,y,engine.px,engine.py,"#ff795530",1);}
        glow(x,y,r+18,col,.055);
        int sides=type==3?6:type==2?3:4;
        double[] pts=new double[sides*2];for(int i=0;i<sides;i++){double angle=a+i*Math.PI*2/sides+Math.PI/4;pts[i*2]=x+Math.cos(angle)*r;pts[i*2+1]=y+Math.sin(angle)*r;}
        polygon(pts,"#112630",true,1);polygon(pts,col,false,type==3?3:2);
        circle(x,y,r*.33,col,2,false);circle(x,y,3,col,1,true);
        if(type==3){circle(x,y,r+10,"#ff795566",1,false);if(!portrait())text("SENTINEL",x,y-r-28,9,CORAL,"center",false);}
        if(hp<1){fill(x-r,y-r-12,r*2,3,"#294149");fill(x-r,y-r-12,r*2*Math.max(0,hp),3,col);}
    }

    @JSBody(params="f",script="requestAnimationFrame(f);") private static native void request(Frame f);
    private static void stickEvent(String kind,double dx,double dy,double radius) {
        TouchStick stick=kind.startsWith("move")?moveStick:aimStick;
        if(kind.endsWith("-end"))stick.release();else stick.set(dx,dy,radius);
        stickKnob(kind.startsWith("move")?"move-pad":"aim-pad",stick.knobX()*radius,stick.knobY()*radius);
    }
    private static void uprightText(String value,double x,double y,int size,String color,boolean bold) {
        if(portrait())screenText(value,350,560+(y-350),size,color,bold);
        else text(value,x,y,size,color,"center",bold);
    }
    @JSBody(params={"action","sticks"},script="""
        var bsCanvas=document.getElementById('game');
        window.bs={c:bsCanvas.getContext('2d'),keys:{},mx:820,my:340,down:false,portrait:false,touch:false,usingTouch:false,viewWidth:0,viewHeight:0};
        var bsMovePad=document.getElementById('move-pad'),bsAimPad=document.getElementById('aim-pad');
        var bsOwners={move:null,aim:null};
        function bsReleasePad(bsName){
            var bsPad=bsName==='move'?bsMovePad:bsAimPad;
            var bsOwner=bsOwners[bsName];bsOwners[bsName]=null;
            if(bsOwner!==null && bsPad.hasPointerCapture(bsOwner))bsPad.releasePointerCapture(bsOwner);
            sticks(bsName+'-end',0,0,1);
        }
        bs.releaseInputs=function(){bs.keys={};bs.down=false;bsReleasePad('move');bsReleasePad('aim');};
        function bsLayout(){
            var bsWasPortrait=bs.portrait;
            bs.portrait=bs.touch && innerHeight>innerWidth;
            document.body.classList.toggle('touch-device',bs.touch);
            document.body.classList.toggle('portrait-arena',bs.portrait);
            var bsWidth=bs.portrait?700:1120,bsHeight=bs.portrait?1120:700;
            if(bsCanvas.width!==bsWidth || bsCanvas.height!==bsHeight){bsCanvas.width=bsWidth;bsCanvas.height=bsHeight;}
            if(bs.viewWidth!==innerWidth || bs.viewHeight!==innerHeight)bs.releaseInputs();
            bs.viewWidth=innerWidth;bs.viewHeight=innerHeight;
            if(bsWasPortrait!==bs.portrait)action('blur');
        }
        function bsUseTouch(){bs.usingTouch=true;if(!bs.touch){bs.touch=true;bsLayout();}}
        bs.touch=matchMedia('(any-pointer: coarse)').matches || navigator.maxTouchPoints>0 || new URLSearchParams(location.search).get('touch')==='1';
        bs.usingTouch=bs.touch;bsLayout();window.addEventListener('resize',bsLayout);
        function bsPoint(bsEvent){
            bs.usingTouch=false;
            var bsBounds=bsCanvas.getBoundingClientRect();
            var bsScreenX=(bsEvent.clientX-bsBounds.left)*bsCanvas.width/bsBounds.width;
            var bsScreenY=(bsEvent.clientY-bsBounds.top)*bsCanvas.height/bsBounds.height;
            bs.mx=bs.portrait?bsScreenY:bsScreenX;bs.my=bs.portrait?700-bsScreenX:bsScreenY;
        }
        bsCanvas.addEventListener('pointermove',function(bsEvent){if(bsEvent.pointerType!=='touch')bsPoint(bsEvent);});
        bsCanvas.addEventListener('pointerdown',function(bsEvent){
            if(bsEvent.pointerType==='touch'){bsUseTouch();return;}
            bsPoint(bsEvent);if(bsEvent.button===0){bs.down=true;bsCanvas.focus({preventScroll:true});}
        });
        window.addEventListener('pointerup',function(bsEvent){if(bsEvent.pointerType!=='touch')bs.down=false;});
        window.addEventListener('pointercancel',function(bsEvent){if(bsEvent.pointerType!=='touch')bs.down=false;});
        bsCanvas.addEventListener('contextmenu',function(bsEvent){bsEvent.preventDefault();});
        function bsBindPad(bsPad,bsName){
            function bsUpdate(bsEvent){
                var bsBounds=bsPad.getBoundingClientRect();
                sticks(bsName,bsEvent.clientX-bsBounds.left-bsBounds.width/2,bsEvent.clientY-bsBounds.top-bsBounds.height/2,Math.min(bsBounds.width,bsBounds.height)*0.35);
            }
            bsPad.addEventListener('pointerdown',function(bsEvent){
                if(document.body.dataset.state!=='PLAY' || bsOwners[bsName]!==null)return;
                bsEvent.preventDefault();bsUseTouch();bsOwners[bsName]=bsEvent.pointerId;
                try{bsPad.setPointerCapture(bsEvent.pointerId);}catch(bsCaptureError){}bsUpdate(bsEvent);
            });
            window.addEventListener('pointermove',function(bsEvent){if(bsOwners[bsName]===bsEvent.pointerId){bsEvent.preventDefault();bsUpdate(bsEvent);}},{passive:false});
            function bsEnd(bsEvent){if(bsOwners[bsName]===bsEvent.pointerId)bsReleasePad(bsName);}
            window.addEventListener('pointerup',bsEnd);window.addEventListener('pointercancel',bsEnd);bsPad.addEventListener('lostpointercapture',bsEnd);
        }
        bsBindPad(bsMovePad,'move');bsBindPad(bsAimPad,'aim');
        document.addEventListener('pointerdown',function(bsEvent){if(bsEvent.pointerType==='touch')bsUseTouch();},{passive:true});
        window.addEventListener('keydown',function(bsEvent){if(['Space','ArrowUp','ArrowDown','ArrowLeft','ArrowRight','Escape'].indexOf(bsEvent.code)>=0)bsEvent.preventDefault();bs.keys[bsEvent.code]=true;if(!bsEvent.repeat)action(bsEvent.code);});
        window.addEventListener('keyup',function(bsEvent){bs.keys[bsEvent.code]=false;});
        window.addEventListener('blur',function(){bs.releaseInputs();action('blur');});
        document.addEventListener('visibilitychange',function(){if(document.hidden){bs.releaseInputs();action('blur');}});
        """) private static native void boot(Action action,StickEvent sticks);
    @JSBody(params="action",script="var bsDash=document.getElementById('dash-touch');bsDash.addEventListener('pointerdown',function(bsEvent){if(!bsDash.disabled){bsEvent.preventDefault();bs.usingTouch=true;action('Space');}});bsDash.addEventListener('click',function(bsEvent){if(bsEvent.detail===0&&!bsDash.disabled)action('Space');});") private static native void bindDash(Action action);
    @JSBody(script="return bs.usingTouch;") private static native boolean usingTouch();
    @JSBody(script="return bs.portrait;") private static native boolean portrait();
    @JSBody(script="bs.releaseInputs();") private static native void resetInput();
    @JSBody(params={"id","x","y"},script="var bsPad=document.getElementById(id);bsPad.style.setProperty('--stick-x',x+'px');bsPad.style.setProperty('--stick-y',y+'px');") private static native void stickKnob(String id,double x,double y);
    @JSBody(params={"cooldown","playing"},script="var bsButton=document.getElementById('dash-touch');bsButton.disabled=!playing || cooldown>0;bsButton.dataset.ready=String(cooldown<=0);bsButton.textContent=cooldown>0?cooldown.toFixed(1)+'s':'DASH ↗';") private static native void dashButton(double cooldown,boolean playing);
    @JSBody(script="bs.c.save();bs.c.setTransform(1,0,0,1,0,0);bs.c.fillStyle='#091b22ed';bs.c.fillRect(110,510,480,110);bs.c.restore();") private static native void screenPanel();
    @JSBody(params={"s","x","y","size","color","bold"},script="var bsContext=bs.c;bsContext.save();bsContext.setTransform(1,0,0,1,0,0);bsContext.font=(bold?'bold ':'')+size+'px monospace';bsContext.fillStyle=color;bsContext.textAlign='center';bsContext.fillText(s,x,y);bsContext.restore();") private static native void screenText(String s,double x,double y,int size,String color,boolean bold);
    @JSBody(params={"id","a"},script="document.getElementById(id).addEventListener('click',function(){a(id);});") private static native void bind(String id,Action a);
    @JSBody(params="code",script="return !!bs.keys[code];") private static native boolean key(String code);
    @JSBody(script="return bs.mx;") private static native double mouseX();
    @JSBody(script="return bs.my;") private static native double mouseY();
    @JSBody(script="return bs.down;") private static native boolean firing();
    @JSBody(script="document.getElementById('game').focus({preventScroll:true});") private static native void focus();
    @JSBody(script="var s=document.querySelector('main');if(document.fullscreenElement){document.exitFullscreen().catch(function(){});}else if(s.requestFullscreen){s.requestFullscreen().catch(function(){});}") private static native void fullscreen();
    @JSBody(params={"id","value"},script="var e=document.getElementById(id);if(e.textContent!==value)e.textContent=value;") private static native void setText(String id,String value);
    @JSBody(params={"id","visible"},script="document.getElementById(id).classList.toggle('active',visible);") private static native void show(String id,boolean visible);
    @JSBody(script="return window.matchMedia('(prefers-reduced-motion: reduce)').matches;") private static native boolean reduced();
    @JSBody(script="try{return Number(localStorage.getItem('bankshot.best'))||0;}catch(e){return 0;}") private static native int loadBest();
    @JSBody(params="v",script="try{localStorage.setItem('bankshot.best',String(v));}catch(e){}") private static native void saveBest(int v);
    @JSBody(script="try{return localStorage.getItem('bankshot.mute')==='1';}catch(e){return false;}") private static native boolean loadMute();
    @JSBody(params="v",script="try{localStorage.setItem('bankshot.mute',v?'1':'0');}catch(e){}") private static native void saveMute(boolean v);
    @JSBody(params={"state","wave","hp","score","x","y","shots","kills","bullets","enemies","aimX","aimY"},script="var bsData=document.body.dataset;bsData.state=state;bsData.wave=wave;bsData.health=hp;bsData.score=score;bsData.playerX=x;bsData.playerY=y;bsData.shots=shots;bsData.kills=kills;bsData.bullets=bullets;bsData.enemies=enemies;bsData.aimX=aimX;bsData.aimY=aimY;") private static native void telemetry(String state,int wave,double hp,int score,double x,double y,int shots,int kills,int bullets,int enemies,double aimX,double aimY);
    @JSBody(params="kind",script="""
        try{var A=window.AudioContext||window.webkitAudioContext;if(!A)return;var a=bs.audio||(bs.audio=new A());if(a.state==='suspended')a.resume();var o=a.createOscillator(),g=a.createGain();var n=a.currentTime;var f=kind===0?440:kind===1?220:kind===2?95:660;o.type=kind===2?'sawtooth':'triangle';o.frequency.setValueAtTime(f,n);o.frequency.exponentialRampToValueAtTime(kind===3?990:f*.35,n+.12);g.gain.setValueAtTime(kind===0?.025:.055,n);g.gain.exponentialRampToValueAtTime(.001,n+.15);o.connect(g);g.connect(a.destination);o.start(n);o.stop(n+.16);}catch(e){}
        """) private static native void tone(int kind);
    @JSBody(script="bs.c.setTransform(1,0,0,1,0,0);bs.c.globalAlpha=1;bs.c.clearRect(0,0,1120,1120);if(bs.portrait)bs.c.setTransform(0,1,-1,0,700,0);") private static native void clear();
    @JSBody(params={"x","y","w","h","color"},script="bs.c.fillStyle=color;bs.c.fillRect(x,y,w,h);") private static native void fill(double x,double y,double w,double h,String color);
    @JSBody(params={"x","y","w","h","color","width"},script="bs.c.strokeStyle=color;bs.c.lineWidth=width;bs.c.strokeRect(x,y,w,h);") private static native void rect(double x,double y,double w,double h,String color,double width);
    @JSBody(params={"x","y","xx","yy","color","w"},script="var bsContext=bs.c;bsContext.strokeStyle=color;bsContext.lineWidth=w;bsContext.beginPath();bsContext.moveTo(x,y);bsContext.lineTo(xx,yy);bsContext.stroke();") private static native void line(double x,double y,double xx,double yy,String color,double w);
    @JSBody(params={"x","y","r","color","w","fill"},script="var bsContext=bs.c;bsContext.beginPath();bsContext.arc(x,y,Math.max(0,r),0,Math.PI*2);if(fill){bsContext.fillStyle=color;bsContext.fill();}else{bsContext.strokeStyle=color;bsContext.lineWidth=w;bsContext.stroke();}") private static native void circle(double x,double y,double r,String color,double w,boolean fill);
    @JSBody(params={"points","color","fill","w"},script="var bsContext=bs.c;bsContext.beginPath();bsContext.moveTo(points[0],points[1]);for(var i=2;i<points.length;i+=2)bsContext.lineTo(points[i],points[i+1]);bsContext.closePath();if(fill){bsContext.fillStyle=color;bsContext.fill();}else{bsContext.strokeStyle=color;bsContext.lineWidth=w;bsContext.stroke();}") private static native void polygon(double[] points,String color,boolean fill,double w);
    @JSBody(params={"s","x","y","size","color","align","bold"},script="var bsContext=bs.c;bsContext.font=(bold?'bold ':'')+size+'px monospace';bsContext.fillStyle=color;bsContext.textAlign=align;bsContext.fillText(s,x,y);") private static native void text(String s,double x,double y,int size,String color,String align,boolean bold);
    @JSBody(params={"x","y","r","color","opacity"},script="var bsContext=bs.c;bsContext.save();bsContext.globalAlpha*=opacity;bsContext.fillStyle=color;bsContext.beginPath();bsContext.arc(x,y,r,0,Math.PI*2);bsContext.fill();bsContext.restore();") private static native void glow(double x,double y,double r,String color,double opacity);
    @JSBody(params="a",script="bs.c.globalAlpha=a;") private static native void alpha(double a);
    @JSBody(params={"x","y"},script="bs.c.translate(x,y);") private static native void translate(double x,double y);
}
