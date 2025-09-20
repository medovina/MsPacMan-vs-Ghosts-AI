package game;

import controllers.ghosts.GhostsActions;
import controllers.pacman.PacManAction;
import game.core.*;

import java.awt.event.KeyListener;
import java.util.Random;

/**
 * One simulator can run one instance of PacMan-vs-Ghosts game.
 * 
 * Can be used for both head/less games.
 * 
 * @author Jimmy
 */
public class PacManSimulator {
	private GameView gv;
	private _G_ game;
	
    private long due; 
    
	public synchronized Game run(final SimulatorConfig config) {
		gv = null;
		game = null;		
		
		// INIT RANDOMNESS
		if (config.game.seed < 0) {
			config.game.seed = new Random(System.currentTimeMillis()).nextInt();
			while (config.game.seed < 0) config.game.seed += Integer.MAX_VALUE;
		}
		
		// INITIALIZE THE SIMULATION
		game = new _G_();
		game.newGame(config.game, config.ghostsController);
		
		// RESET CONTROLLERS
		if (config.ghostsController != null) config.ghostsController.reset(game);
		config.pacManController.reset(game);

		// INITIALIZE THE VIEW
		if (config.visualize) {
			gv = new GameView(game, 3);
			gv.showGame();
			
			if (config.pacManController instanceof KeyListener) {				
				gv.getFrame().addKeyListener((KeyListener)config.pacManController);
			}
			if (config.ghostsController != null && config.ghostsController instanceof KeyListener) {				
				gv.getFrame().addKeyListener((KeyListener)config.ghostsController);
			}
		} 
		
		int lastLevel = game.getCurLevel();
		
		// START THE GAME
		try {
			while(!game.gameOver())
			{
				due = System.currentTimeMillis() + config.thinkTimeMillis;

				if (!game.isSuspended()) {
                    config.pacManController.tick(game.copy(), due);
                    boolean pacManLag = System.currentTimeMillis() > due;
                    if (pacManLag)
                        System.out.println("[SIMULATOR] PacMan took too long to choose a move!");

                    if (config.ghostsController != null)
                        config.ghostsController.tick(game, due);
                    if (!pacManLag && System.currentTimeMillis() > due)
                        System.out.println("[SIMULATOR] Ghosts took too long to choose moves!");
				}

                if (config.visualize) {
                    long sleepTime = due - System.currentTimeMillis();
                    if (sleepTime > 4) {
                        try {
                           Thread.sleep(sleepTime);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
		        
		        // OBTAIN ACTIONS
		        PacManAction  pacManAction  = config.pacManController.getAction().clone();
                GhostsActions ghostsActions =
                    config.ghostsController == null ? null :
                                                      config.ghostsController.getActions().clone();
				
		        // SIMULATION PAUSED?
		        boolean advanceGame = true;
                if (pacManAction.pauseSimulation) {
                    if (!pacManAction.nextFrame) {
                        advanceGame = false;
                    }
                    config.pacManController.getAction().nextFrame = false;
                }
		        
		        // ADVANCE GAME
		        if (advanceGame) {
		        	int pacManLives = game.getLivesRemaining();
		        	
			        game.advanceGame(pacManAction.get().index, ghostsActions);
			        
			        // NEW LEVEL?
			        if (game.getCurLevel() != lastLevel) {
			        	lastLevel=game.getCurLevel();
			        	
			        	// INFORM CONTROLLERS
			        	config.pacManController.nextLevel(game.copy());
                        if (config.ghostsController != null)
                            config.ghostsController.nextLevel(game.copy());
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

	/**
	 * Run simulation according to the configuration.
	 */
	public static Game play(SimulatorConfig config) {
		PacManSimulator simulator = new PacManSimulator();
		return simulator.run(config);		
	}
}
