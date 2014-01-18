package integrated;

import java.util.Arrays;
import java.util.Random;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.MapLocation;
import battlecode.common.Robot;
import battlecode.common.RobotController;
import battlecode.common.Team;
import battlecode.common.TerrainTile;

public class HQ {
	
	static boolean initializerRun = false;
    static double cowDensMap[][];
    static int mapY, mapX;
    static MapLocation desiredPASTRs[];
    static  Team team;
    static Team enemy;
    static int idealNumPastures;
    static int storedCurrNumPastures = 0;
    static boolean constructNT = false;
	static MapLocation enemyHQ;
	static MapLocation teamHQ;
	static boolean rush = false;
    
    static int[] squads = new int[20];
    
    static int[][] terrainMap;
	final static int	NORMAL = 10;
	final static int	ROAD = 3;
	final static int	WALL = 1000;
	final static int	OFFMAP = 99999;
    
	static RobotController hq;
	static Random rand;

    static int[] robotTypeCount = {0,0,0,0};

	public static void runHeadquarters(RobotController rc) throws GameActionException {
		
		if(!initializerRun) {
    		//spawns a robot on the first round: defender on squad 3
    		tryToSpawn(rc, 0);
    		rc.broadcast(0, 300);
    	
			initializeGameVars(rc);
		}
		
		updateSquadLocs(rc);
		updateRobotDistro(rc);
		
		Robot[] enemyRobots = rc.senseNearbyGameObjects(Robot.class, rc.getType().attackRadiusMaxSquared, enemy);
		if(enemyRobots.length > 0)
			Util.indivShootNearby(rc, enemyRobots);
		else
			spawnRobot(rc);
		
		rc.yield();
	}
	
	public static void initializeGameVars(RobotController rc) throws GameActionException{
    	hq = rc;
    	
    	team =  hq.getTeam();
    	enemy = team.opponent();
    	cowDensMap = hq.senseCowGrowth();
    	mapY = cowDensMap.length;
    	mapX = cowDensMap[0].length;
    	idealNumPastures = computeNumPastures();
    	enemyHQ = rc.senseEnemyHQLocation();
    	teamHQ = rc.senseHQLocation();
    	
    	desiredPASTRs = findPastureLocs();
    	System.out.println("Desired pastures : " + Arrays.deepToString(desiredPASTRs));
    	
    	createTerrainMap();
    	initializerRun = true;
    	
    	rush = startRush(rc);
    	
    	rand = new Random(17);
    }
	
	//Put into channels correct pasture locations and enemy locations
	static void updateSquadLocs(RobotController rc) throws GameActionException{
		for(int i = 0; i < desiredPASTRs.length; i++)
			rc.broadcast(i+3, (rc.readBroadcast(i)%10000)*10000 + Util.locToInt(desiredPASTRs[i]));
		
		MapLocation[] enemyPASTRs = rc.sensePastrLocations(enemy);
		
		if(rush)
			rc.broadcast(11, (rc.readBroadcast(11)%10000)*10000 + Util.locToInt(enemyHQ));
		else
			rc.broadcast(11, (rc.readBroadcast(11)%10000)*10000 + Util.locToInt(enemyPASTRs[0]));
		
		for(int i = 0; i < enemyPASTRs.length; i++) {
			rc.broadcast(i+12, (rc.readBroadcast(i)%10000)*10000 + Util.locToInt(enemyPASTRs[i]));	
		}
	}
	
	static boolean startRush(RobotController rc){
		if(enemyHQ.distanceSquaredTo(teamHQ) < 1800){
			System.out.println("START-OF-GAME RUSHING THE OTHER TEAM");
			return true;
		} else {
			System.out.println("STARTING ECONOMY DEVELOPMENT PREFERRED");
			return false;
		}
	}
	
	//Keep track of deaths
	static void updateRobotDistro(RobotController rc) throws GameActionException{
		
		//Channel 1: distress: [SS][T][SS][T]...SS=squad, and T = type of distressed robots
		int in  = rc.readBroadcast(1);
		int numRobots = (int) (Math.log10(in)+1)/3;
		
		for(int i = 0; i < numRobots; i++){
			int j = (int) (in/Math.pow(1000,i))%1000;
			int type = j%10;
			int squad = j/10;
			
			//subtract from squad count signal and robot type count
			robotTypeCount[type]--;
			int k = rc.readBroadcast(squad);
			rc.broadcast(squad,(k/10000-1)*10000+k%10000);
		}
	}
	
	static void spawnRobot(RobotController rc) throws GameActionException{

		if(rc.senseRobotCount()<GameConstants.MAX_ROBOTS && rc.isActive()){
			
			int squad = nextSquadNum(rc);
			boolean spawnSuccess = false;
			
			if(squad > 10) {
				spawnSuccess = tryToSpawn(rc, 1);
				if(spawnSuccess) {
					int j = Util.assignmentToInt(squad, 1);
					rc.broadcast(0, j);
					System.out.println("Spawned an attacker:" + j);
				}
				
			} else if (squad < 11 && robotTypeCount[0] < 3*desiredPASTRs.length) {
				spawnSuccess = tryToSpawn(rc, 0);
				if(spawnSuccess){
					int j = Util.assignmentToInt(squad, 0);
					rc.broadcast(0, j);
					System.out.println("Spawned a defender: " + j);
				}
				
			} else if(constructNT){
				spawnSuccess = tryToSpawn(rc, 3);
				if(spawnSuccess){
					int j = Util.assignmentToInt(squad, 3);
					rc.broadcast(0, j);
					System.out.println("Spawned a NT precursor: " + j);
					constructNT = false;
				}
				
			} else if (squad < 11 && robotTypeCount[2] < desiredPASTRs.length) {
				spawnSuccess = tryToSpawn(rc, 2);
				if(spawnSuccess){
					int j = Util.assignmentToInt(squad, 2);
					rc.broadcast(0, j);
					System.out.println("Spawned a pasture precursor: " + j);
					constructNT = true;
				}


			}
			
			if(spawnSuccess)
				rc.broadcast(squad, rc.readBroadcast(squad)+10000);
		}
	}
	
	//Determines squad of robot to by spawned next 
	private static int nextSquadNum(RobotController rc) throws GameActionException {
		
		//If starting out a rush, spawn enough attacking squads.
		if(rush && robotTypeCount[1] < 6) {
			System.out.println("Going to spawn rush attacker");
			for(int i = 11; i < 21; i++){
				if((rc.readBroadcast(i)/10000)%10<6)
					return i;
			}
		} 
		
		//Else if didn't establish pastures yet, need defensive squads
		if(robotTypeCount[2] < desiredPASTRs.length) {
			for(int i = 3; i < 10; i++){
				if((rc.readBroadcast(i)/10000)%10<5)
					return i;
			}
			
		} else { //else spawn attackers
			for(int i = 11; i < 21; i++){
				if((rc.readBroadcast(i)/10000)%10<6)
					return i;
			}
		}
		
		return 3;
	}

	static boolean tryToSpawn(RobotController rc, int type) throws GameActionException {
		if(rc.isActive() && rc.senseRobotCount() < GameConstants.MAX_ROBOTS) {
			Direction dir = Util.findDirToMove(rc);
			if(dir != null) {
				rc.spawn(dir);
				robotTypeCount[type]++;
				return true;
			}
		}
		return false;
	}
	
	private static int computeNumPastures() {
		return 2;
	}

	static MapLocation[] findPastureLocs() throws GameActionException {
		//returns a MapLocation array with the best pastures
		
		MapLocation pstrLocs[] = new MapLocation[idealNumPastures];
		double pstrCowDens[] = new double[idealNumPastures];
		
		//Fill default
		for (int i = 0; i < idealNumPastures; i++) {
			pstrLocs[i] = new MapLocation(mapX/2, mapY/2);
		}
		
		//The first pasture will be right next to the HQ
		pstrLocs[0] = findHQpstr();
		
		//The next pastures are decided based on cow density
		//Slides a 3x3 window across the entire map, intervals of three and returns windows with highest 
		for(int i = 0; i < mapY-3; i+=4){
			for(int j = 0; j < mapX-3; j+=4){
				
				double sum = (cowDensMap[i][j] + cowDensMap[i+1][j] + cowDensMap[i+2][j] 
							+ cowDensMap[i][j+1] + cowDensMap[i+1][j+1] + cowDensMap[i+2][j+1]
							+ cowDensMap[i][j+2] + cowDensMap[i+1][j+2] + cowDensMap[i+2][j+2]);
				
				//More weight = farther away from HQ = bad
				double weight = hq.getLocation().distanceSquaredTo(new MapLocation(j,i));
				double weight1 = hq.senseEnemyHQLocation().distanceSquaredTo(new MapLocation(j,i));
				
				for(int k = 1; k < idealNumPastures; k++){
					
					//Balancing profit in pasture productivity vs. distance: (sum-weight/10)
					if((sum-weight/weight1)>pstrCowDens[k]){
						pstrLocs[k] = new MapLocation(j+1, i+1);
						
						pstrCowDens[k] = (sum-weight/weight1);
						break;
					}
				}
			}
		}

		System.out.println(pstrLocs);
		return pstrLocs;
	}
		
	private static MapLocation findHQpstr() {
		// returns the first pstr location, close to the HQ so it can be defended well
		MapLocation HQ = hq.senseHQLocation();
		MapLocation enemyHQ = hq.senseEnemyHQLocation();
		Direction away_from_enemy = enemyHQ.directionTo(HQ);
		MapLocation HQpstr = null;
		
		if (hq.canMove(away_from_enemy)) { //check to see if that spot exists
			HQpstr = HQ.add(away_from_enemy);
		} else { //that spot is probably in a wall, which would be weird, but possible
			for (Direction i:Util.allDirections) {
				if (hq.canMove(i)) {
					return HQ.add(away_from_enemy);
				}
			}
		}
		return HQpstr;
	}
	
	public static void createTerrainMap(){

		//Get cow density field and map dimensions
		double cowDensMap[][] = hq.senseCowGrowth();
		int mapY = cowDensMap.length, mapX = cowDensMap[0].length;

		//Initialize terrain map array
		terrainMap = new int[mapY][mapX]; 

		//Scan over map to identify types of terrain at each location
		for(int i = 0; i < mapY; i++){
			for(int j = 0; j < mapX; j++){
				TerrainTile t = hq.senseTerrainTile(new MapLocation(j, i));
				if(t==TerrainTile.valueOf("NORMAL"))
					terrainMap[i][j] = NORMAL;
				else if(t==TerrainTile.valueOf("ROAD"))
					terrainMap[i][j] = ROAD;
				else if(t==TerrainTile.valueOf("VOID"))
					terrainMap[i][j] = WALL;
				else
					terrainMap[i][j] = OFFMAP;
			}
		}
	}

}
