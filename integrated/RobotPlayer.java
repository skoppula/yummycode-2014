package integrated;

//things to do:
//defend pastrs that are under attack, or at least consider defending them
//battlecry when charging into battle -> concerted effort
//something like the opposite of a battlecry, when you're sure you're outnumbered

/*
 * Channel 0: spawning signal: squad*100+type
 * Channel 1: distress: [SS][T][SS][T]...SS=squad, and T = type of distressed robots
 * Channel 2: current pastures and noise towers: [D][D][D]...where D=0 indicates no PASTR/NT, D=1 indicates PASTR, D=2 PASTR & NT set up 
 * Channel 3-9: defensive squad locations & corresponding pastr/NT locations: [xx][yy][A][XX][YY], (xx,yy) = average loc of swam, A = count robots in squad, (XX,YY) = target
 * Channel 10: if experiencing rush 1; else 0
 * Channel 11-20: offensive squad locations & corresponding pasr/NT locations
 * 
 */

import battlecode.common.*;

public class RobotPlayer{
	
	public static void run(RobotController rc) throws GameActionException{
		
		int id = rc.getRobot().getID();
    		RobotType type = rc.getType();
    	
		//read from channel 0: get squad and role
    	int assignment = rc.readBroadcast(Util.spawnchannel);
    	
    	if(type == RobotType.HQ) {
    		if (rc.getMapHeight() > 40) {
    			rc.broadcast(0, 300);
        		rc.broadcast(3, Util.locToInt(rc.senseHQLocation()));
    		}else {
    			rc.broadcast(0, 1101);
    			rc.broadcast(11, Util.locToInt(rc.senseEnemyHQLocation()));
    		}
    		
    		HQ.tryToSpawn(rc, 0);
    	} else if (type == RobotType.SOLDIER)
    		rc.broadcast(id, assignment);
    	else if (type==RobotType.PASTR){
    		PASTR.getSquad(rc);
    		rc.broadcast(id+1, Clock.getRoundNum());
	}
		try {
        	while(true) {
        		
        		if(type == RobotType.HQ)
                	HQ.runHeadquarters(rc);
        		
        		else if (type == RobotType.PASTR)
        			PASTR.maintainPasture(rc);
        			
        		else if(type == RobotType.NOISETOWER)
        			NOISE.maintainNoiseTower(rc);
        		
        		else {
        			COWBOY.runCowboy(rc, assignment);
        		}
        		
        		rc.yield();
        	}
		} catch (Exception e){
				e.printStackTrace();
		}
			
	}
	
}
