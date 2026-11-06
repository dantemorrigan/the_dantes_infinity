import com.dantemorrigan.gauntlet.cosmos.CelestialRotation;

public class CelestialRotationCheck {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        for(String id:new String[]{"sun","mercury","venus","earth","moon","mars","jupiter","saturn","uranus","neptune"}){
            var spin=CelestialRotation.spin(id);
            float quarter=CelestialRotation.yaw(id,spin.days()*24000/4);
            check(Math.abs(((quarter-spin.phase()+360)%360)-90)<.001,"Wrong sidereal period: "+id);
            check(Math.abs(CelestialRotation.yaw(id,0)-CelestialRotation.yaw(id,spin.days()*24000))<.001,"Rotation did not wrap: "+id);
            double t=9_000_000_000d;
            float step=(CelestialRotation.yaw(id,t+1)-CelestialRotation.yaw(id,t)+360)%360;
            check(step<.04,"Large world time caused an angular jump: "+id);
        }
        check(CelestialRotation.spin("venus").tilt()>90 && CelestialRotation.spin("uranus").tilt()>90,"Retrograde axes lost");
        check(CelestialRotation.spin("earth").tilt()<90,"Earth axis reversed");
        check(CelestialRotation.spin("jupiter").days()<CelestialRotation.spin("earth").days(),"Jupiter must rotate faster than Earth");
        System.out.println("CELESTIAL ROTATION: periods, saved clock continuity and retrograde axes passed");
    }
}
