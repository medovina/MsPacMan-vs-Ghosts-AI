package controllers.ghosts.examples;

import controllers.ghosts.*;
import game.core.Game;

public final class RandomGhosts extends GhostsControllerBase
{	
	
	public RandomGhosts() {
		super(Game.NUM_GHOSTS);
	}

	public IGhostsController copy() {
		return new RandomGhosts();
	}

	@Override
	public void tick(Game game, long timeDue) {
		int[] directions=new int[Game.NUM_GHOSTS];
		
		//Chooses a random LEGAL action if required. Could be much simpler by simply returning
		//any random number of all of the ghosts
		for(int i=0;i<directions.length;i++)
			if(game.ghostRequiresAction(i))
			{			
				int[] possibleDirs=game.getPossibleGhostDirs(i);			
				directions[i]=possibleDirs[game.rand().nextInt(possibleDirs.length)];
			}
		
		input.set(directions);
	}
}
