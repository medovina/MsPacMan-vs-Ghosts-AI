package game;

import controllers.ghosts.GhostsActions;
import controllers.pacman.PacManAction;
import game.core.G;
import game.core.Game;
import game.core.GameView;
import game.core.Replay;
import game.core._G_;

import java.awt.event.KeyListener;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * One simulator can run one instance of PacMan-vs-Ghosts game.
 * 
 * Can be used for both head/less games.
 * 
 * @author Jimmy
 */
public class PacManSimulator {
	
	private SimulatorConfig config;
	
	private GameView gv;

	private _G_ game;
	
    private long due; 
    
    // REPLAY STUFF
    private StringBuilder replayData;
    private boolean replayFirstWrite;
	
	public synchronized Game run(final SimulatorConfig config) {
		// RESET INSTANCE & SAVE CONFIG
		reset(config);
		
		// INIT RANDOMNESS
		if (config.game.seed < 0) {
			config.game.seed = new Random(System.currentTimeMillis()).nextInt();
			while (config.game.seed < 0) config.game.seed += Integer.MAX_VALUE;
		}
		G.rnd = new Random(config.game.seed);
		
		// INITIALIZE THE SIMULATION
		game = new _G_();
		game.newGame(config.game);
		
		// RESET CONTROLLERS
		config.pacManController.reset(game);
		if (config.ghostsController != null) config.ghostsController.reset(game);

		// INITIALIZE THE VIEW
		if (config.visualize) {
			gv = new GameView(game, config.visualizationScale2x ? 2 : 1);
			gv.showGame();
			
			if (config.pacManController instanceof KeyListener) {				
				gv.getFrame().addKeyListener((KeyListener)config.pacManController);
			}
			if (config.ghostsController != null && config.ghostsController instanceof KeyListener) {				
				gv.getFrame().addKeyListener((KeyListener)config.ghostsController);
			}
		} 
		
		// SETUP REPLAY RECORDING
		int lastLevel = game.getCurLevel();
		if (config.replay) {
			replayData = new StringBuilder();
			replayFirstWrite = true;
		}
		
		// START CONTROLLERS (threads auto-start during instantiation)
		ThinkingThread pacManThread = 
			new ThinkingThread(
				"PAC-MAN",
				new IThinkingMethod() {
					@Override
					public void think() {
						PacManSimulator.this.config.pacManController.tick(game.copy(), due);		
					}
				}
			);
		ThinkingThread ghostsThread =
			new ThinkingThread(
				"GHOSTS",
				new IThinkingMethod() {
					@Override
					public void think() {
						if (PacManSimulator.this.config.ghostsController != null) PacManSimulator.this.config.ghostsController.tick(game, due);			
					}
				}
			);
		 
		// START THE GAME
		try {
			while(!game.gameOver())
			{
				due = System.currentTimeMillis() + config.thinkTimeMillis;
				
				// WAKE UP THINKING THREADS
				thinkingLatch = new CountDownLatch(2);
				
				long start = System.currentTimeMillis();
				
				pacManThread.alert();
				ghostsThread.alert();
				
				// GIVE THINKING TIME
		        try{		        			        	
		        	thinkingLatch.await(config.thinkTimeMillis, TimeUnit.MILLISECONDS);
		        	
		        	if (config.visualize) {
		        		if (System.currentTimeMillis() - start < config.thinkTimeMillis) {
		        			long sleepTime = config.thinkTimeMillis - (System.currentTimeMillis() - start);
		        			if (sleepTime > 4) {
		        				Thread.sleep(sleepTime);
		        			}
		        		}
		        	}
		        } catch(Exception e) {		        	
		        }
		        
		        if (pacManThread.thinking) {
		        	System.out.println("[SIMULATOR] PacMan is still thinking!");
		        }
		        if (ghostsThread.thinking) {
		        	System.out.println("[SIMULATOR] Ghosts are still thinking!");
		        }
		        
		        thinkingLatch = null;
		        
		        // OBTAIN ACTIONS
		        PacManAction  pacManAction  = config.pacManController.getAction().clone();
		        GhostsActions ghostsActions = (config.ghostsController == null ? null : config.ghostsController.getActions().clone());
				
		        // SIMULATION PAUSED?
		        boolean advanceGame = true;
		        if (config.mayBePaused) {
			        if (pacManAction.pauseSimulation || (ghostsActions != null && ghostsActions.pauseSimulation)) {
			        	if (!pacManAction.nextFrame && (ghostsActions == null || !ghostsActions.nextFrame)) {
			        		advanceGame = false;
			        	}
			        	config.pacManController.getAction().nextFrame = false;
			        	if (config.ghostsController != null) config.ghostsController.getActions().nextFrame = false;
			        }
		        }
		        
		        // ADVANCE GAME
		        if (advanceGame) {
		        	int pacManLives = game.getLivesRemaining();
		        	
			        int replayStep[] = game.advanceGame(pacManAction, ghostsActions);
			        
			        // SAVE ACTIONS TO REPLAY
			        if (config.replay && replayStep != null) {
			        	// STORE ACTIONS
			        	storeActions(replayStep, game.getCurLevel()==lastLevel);
			        }
			        
			        // NEW LEVEL?
			        if (game.getCurLevel() != lastLevel) {
			        	lastLevel=game.getCurLevel();
			        	
			        	// INFORM CONTROLLERS
			        	config.pacManController.nextLevel(game.copy());
			    		if (config.ghostsController != null) config.ghostsController.nextLevel(game.copy());
			    		
			    		// FLUSH REPLAY DATA TO FILE
			    		if (config.replay) {
			    			Replay.saveActions(config.game, (config.ghostsController == null ? 0 : config.ghostsController.getGhostCount()), replayData.toString(), config.replayFile, replayFirstWrite);
			        		replayFirstWrite = false;
			        		replayData = new StringBuilder();
			    		}
			        }
			        
			        // PAC MAN KILLED?
			        if (pacManLives != game.getLivesRemaining()) {
			        	config.pacManController.killed();
			        }
		        }
		        
		        // VISUALIZE GAME
		        if (config.visualize) {
		        	gv.repaint();
		        }
			}
		} finally {		
			// KILL THREADS
			pacManThread.kill();
			ghostsThread.kill();
			
			// SAVE REPLAY DATA
			if (config.replay) {
				Replay.saveActions(config.game, (config.ghostsController == null ? 0 : config.ghostsController.getGhostCount()), replayData.toString(), config.replayFile, replayFirstWrite);
			}
			
			// CLEAN UP
			if (config.visualize) {
				if (config.pacManController instanceof KeyListener) {				
					gv.getFrame().removeKeyListener((KeyListener)config.pacManController);
				}
				if (config.ghostsController instanceof KeyListener) {				
					gv.getFrame().removeKeyListener((KeyListener)config.ghostsController);
				}
				
				gv.getFrame().setTitle("[FINISHED]");
				gv.repaint();
			}					
		}
		
		return game;
	}

	private void reset(SimulatorConfig config) {
		this.config = config;
		
		gv = null;
		game = null;		
	}
	
	private void storeActions(int[] replayStep, boolean newLine) {
		replayData.append( (game.getTotalTime()-1) + "\t" );
	
	    for (int i=0;i < replayStep.length; i++) {
	    	replayData.append(replayStep[i]+"\t");
	    }
	
	    if(newLine) {
	    	replayData.append("\n");
	    }
	}
	
	private interface IThinkingMethod {
		public void think();
	}
	
	private CountDownLatch thinkingLatch;
	
	private class ThinkingThread extends Thread 
	{
		public boolean thinking = false;
	    private IThinkingMethod method;
	    private boolean alive;
	    
	    public ThinkingThread(String name, IThinkingMethod method) 
	    {
	    	super(name);
	        this.method = method;
	        alive=true;
	        start();
	    }

	    public synchronized  void kill() 
	    {
	        alive=false;
	        notify();
	    }
	    
	    public synchronized void alert()
	    {
	        notify();
	    }

	    public void run() 
	    {
	    	 try {
	        	while(alive) 
		        {
		        	try {
		        		synchronized(this)
		        		{
	        				wait(); // waked-up via alert()
		                }
		        	} catch(InterruptedException e)	{
		                e.printStackTrace();
		            }
	
		        	if (alive) {
		        		thinking = true;
		        		method.think();
		        		thinking = false;
		        		try {
		        			thinkingLatch.countDown();
		        		} catch (Exception e) {
		        			// thinkingLatch may be nullified...
		        		}
		        	} 
		        	
		        }
	        } finally {
	        	alive = false;
	        }
	    }
	}
	
	/**
	 * Run simulation according to the configuration.
	 */
	public static Game play(SimulatorConfig config) {
		PacManSimulator simulator = new PacManSimulator();
		return simulator.run(config);		
	}
}
