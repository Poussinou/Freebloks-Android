package de.saschahlusiak.freebloks.game;

import de.saschahlusiak.freebloks.R;
import de.saschahlusiak.freebloks.controller.Spielleiter;
import de.saschahlusiak.freebloks.model.Player;
import de.saschahlusiak.freebloks.model.Spiel;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.TranslateAnimation;
import android.widget.TextView;

public class GameFinishActivity extends Activity {
	public static final int RESULT_NEW_GAME = RESULT_FIRST_USER + 1;
	public static final int RESULT_SHOW_MENU = RESULT_FIRST_USER + 2;

	TextView place;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.game_finish_activity);
		
		place = (TextView) findViewById(R.id.place);
		
		Spielleiter spiel = (Spielleiter)getIntent().getSerializableExtra("game");
		setData(spiel);
		
		findViewById(R.id.new_game).setOnClickListener(new OnClickListener() {			
			@Override
			public void onClick(View v) {
				setResult(RESULT_NEW_GAME);
				finish();
			}
		});
		findViewById(R.id.show_main_menu).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				setResult(RESULT_SHOW_MENU);
				finish();
			}
		});
	}
	
	public void setData(Spielleiter spiel) {
		int place[] = { 0, 1, 2, 3 };
		ViewGroup t[] = new ViewGroup[4];
		/* TODO: generalize */
		final int colors[] = {
				Color.rgb(0, 0, 96),
				Color.rgb(128, 128, 0),
				Color.rgb(96, 0, 0),
				Color.rgb(0, 96, 0),
		};
		/* TODO: translate */
		final String names[] = {
				"Blue",
				"Yellow",
				"Red",
				"Green"
		};
		
		if (spiel == null)
			return;
		
		int i = 0;
		int max = Spiel.PLAYER_MAX - 1;
		if (spiel.m_gamemode == Spielleiter.GAMEMODE_2_COLORS_2_PLAYERS) {
			place[1] = 2;
			place[2] = 1;
			max = 1;
		}
		while ( i < max )
		{
			if (spiel.get_player(place[i]).m_stone_points_left > spiel.get_player(place[i + 1]).m_stone_points_left) {
				int bla = place[i];
				place[i] = place[i + 1];
				place[i + 1] = bla;
				i = 0;
			}else i++;
		}
		
		t[0] = (ViewGroup) findViewById(R.id.place1);
		t[1] = (ViewGroup) findViewById(R.id.place2);
		t[2] = (ViewGroup) findViewById(R.id.place3);
		t[3] = (ViewGroup) findViewById(R.id.place4);
		
		/* TODO: combine yellow/green, blue/red on 4_COLORS_2_PLAYERS */
		if (spiel.m_gamemode == Spielleiter.GAMEMODE_2_COLORS_2_PLAYERS) {
			t[2].setVisibility(View.GONE);
			t[3].setVisibility(View.GONE);
		} else {
			t[2].setVisibility(View.VISIBLE);
			t[3].setVisibility(View.VISIBLE);
		}

		this.place.setText(R.string.game_finished);

		for (i = 0; i < 4; i++) {
			String name;
			Player p = spiel.get_player(place[i]);
			/* TODO: translate */
			name = names[place[i]];
			
			((TextView)t[i].findViewById(R.id.name)).setText(name);
			t[i].findViewById(R.id.name).clearAnimation();
			((TextView)t[i].findViewById(R.id.points)).setText(String.format("-%d points", p.m_stone_points_left));
			((TextView)t[i].findViewById(R.id.stones)).setText(String.format("%d stones", p.m_stone_count));
			t[i].setBackgroundColor(colors[place[i]]);
			
			AnimationSet set = new AnimationSet(false);
			Animation a = new AlphaAnimation(0.0f, 1.0f);
			a.setStartOffset(i * 100);
			a.setDuration(600);
			a.setFillBefore(true);
			set.addAnimation(a);
			a = new TranslateAnimation(
					TranslateAnimation.RELATIVE_TO_SELF, 
					-1, 
					TranslateAnimation.RELATIVE_TO_SELF, 
					0, 
					TranslateAnimation.RELATIVE_TO_SELF, 
					0, 
					TranslateAnimation.RELATIVE_TO_SELF, 
					0);
			a.setStartOffset(200 + i * 100);
			a.setDuration(600);
			a.setFillBefore(true);
			set.addAnimation(a);
			t[i].startAnimation(set);
			
			if (spiel.is_local_player(place[i])) {
				a = new TranslateAnimation(
						TranslateAnimation.RELATIVE_TO_SELF, 
						0, 
						TranslateAnimation.RELATIVE_TO_SELF, 
						0.4f, 
						TranslateAnimation.RELATIVE_TO_SELF, 
						0, 
						TranslateAnimation.RELATIVE_TO_SELF, 
						0);
				a.setDuration(300);
				a.setInterpolator(new DecelerateInterpolator());
				a.setRepeatMode(Animation.REVERSE);
				a.setRepeatCount(Animation.INFINITE);

				((TextView)t[i].findViewById(R.id.name)).setTextColor(Color.WHITE);
				((TextView)t[i].findViewById(R.id.name)).setTypeface(Typeface.DEFAULT_BOLD);

				t[i].findViewById(R.id.name).startAnimation(a);
				this.place.setText(String.format("Place %d", i + 1));
			}
		}
	}
}
