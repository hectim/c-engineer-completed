package com.github.m0bilebtw;

import com.google.inject.Provides;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import javax.inject.Inject;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;
import java.util.regex.Pattern;

@Slf4j
@PluginDescriptor(
	name = "Gachi Flip Flops",
	description = "GachiGasm every time you do something cool",
	tags = {"gachi", "stats", "levels", "quests", "diary", "announce", "meme"}
)

public class CEngineerCompletedPlugin extends Plugin
{
	@Inject
	private Client client;

	@Getter(AccessLevel.PACKAGE)
	@Inject
	private ClientThread clientThread;

	@Inject
	private SoundEngine soundEngine;

	@Inject
	private CEngineerCompletedConfig config;

	private final String[] deathSounds = new String[]{ "/rip-ears.wav", "/daxgasm.wav", "/fuckyou_N4ocxxs.wav", "/spank-3.wav"};
	private final String[] longSounds = new String[]{ "/boi_SG958v4.wav", "/s-stands-for-suction-noot_6Id1unB.wav"};
	private final String[] sounds = new String[]{
			"/cumming.wav",
			"/ok-gachihyper5.wav",
			"/boy-next-door.wav",
			"/daxgasm.wav",
			"/deep-dark-fantasies.wav",
			"/fuckyou_N4ocxxs.wav",
			"/iam-cumming.wav",
			"/suck.wav",
			"/ok-gachihyper3.wav",
			"/ok-gachihyper6.wav",
			"/our-daddy-told-us-not-to-be-ashamed.wav" ,
			"/spank-3.wav",
			"/on.wav",
			"/amazing.wav",
			"/thats-power-son.wav"
	};
	private final Random rnd = new Random();

	private String randomSound(String[] options) {
		return options[rnd.nextInt(options.length)];
	}

	private final Varbits[] varbitsAchievementDiaries = {
			Varbits.DIARY_ARDOUGNE_EASY, Varbits.DIARY_ARDOUGNE_MEDIUM, Varbits.DIARY_ARDOUGNE_HARD, Varbits.DIARY_ARDOUGNE_ELITE,
			Varbits.DIARY_DESERT_EASY, Varbits.DIARY_DESERT_MEDIUM, Varbits.DIARY_DESERT_HARD, Varbits.DIARY_DESERT_ELITE,
			Varbits.DIARY_FALADOR_EASY, Varbits.DIARY_FALADOR_MEDIUM, Varbits.DIARY_FALADOR_HARD, Varbits.DIARY_FALADOR_ELITE,
			Varbits.DIARY_KANDARIN_EASY, Varbits.DIARY_KANDARIN_MEDIUM, Varbits.DIARY_KANDARIN_HARD, Varbits.DIARY_KANDARIN_ELITE,
			Varbits.DIARY_KARAMJA_EASY, Varbits.DIARY_KARAMJA_MEDIUM, Varbits.DIARY_KARAMJA_HARD, Varbits.DIARY_KARAMJA_ELITE,
			Varbits.DIARY_KOUREND_EASY, Varbits.DIARY_KOUREND_MEDIUM, Varbits.DIARY_KOUREND_HARD, Varbits.DIARY_KOUREND_ELITE,
			Varbits.DIARY_LUMBRIDGE_EASY, Varbits.DIARY_LUMBRIDGE_MEDIUM, Varbits.DIARY_LUMBRIDGE_HARD, Varbits.DIARY_LUMBRIDGE_ELITE,
			Varbits.DIARY_MORYTANIA_EASY, Varbits.DIARY_MORYTANIA_MEDIUM, Varbits.DIARY_MORYTANIA_HARD, Varbits.DIARY_MORYTANIA_ELITE,
			Varbits.DIARY_VARROCK_EASY, Varbits.DIARY_VARROCK_MEDIUM, Varbits.DIARY_VARROCK_HARD, Varbits.DIARY_VARROCK_ELITE,
			Varbits.DIARY_WESTERN_EASY, Varbits.DIARY_WESTERN_MEDIUM, Varbits.DIARY_WESTERN_HARD, Varbits.DIARY_WESTERN_ELITE,
			Varbits.DIARY_WILDERNESS_EASY, Varbits.DIARY_WILDERNESS_MEDIUM, Varbits.DIARY_WILDERNESS_HARD, Varbits.DIARY_WILDERNESS_ELITE
	};
	private static final Pattern COLLECTION_LOG_ITEM_REGEX = Pattern.compile("New item added to your collection log:.*");
	private static final Pattern COMBAT_TASK_REGEX = Pattern.compile("Congratulations, you've completed an? (?:\\w+) combat task:.*");
	private static final Pattern QUEST_REGEX = Pattern.compile("Congratulations, you've completed a quest:.*");

	private final Map<Skill, Integer> oldExperience = new EnumMap<>(Skill.class);
	private final Map<Varbits, Integer> oldAchievementDiaries = new EnumMap<>(Varbits.class);

	private int ticksSinceLogin = 0;
	private boolean resetTicks = false;

	@Override
	protected void startUp() throws Exception
	{
		clientThread.invoke(this::setupOldMaps);
	}

	@Override
	protected void shutDown() throws Exception
	{
		oldExperience.clear();
		oldAchievementDiaries.clear();
		collectedGroundItems.clear();
	}

	private void setupOldMaps() {
		if (client.getGameState() != GameState.LOGGED_IN) {
			oldExperience.clear();
			oldAchievementDiaries.clear();
		} else {
			for (final Skill skill : Skill.values()) {
				oldExperience.put(skill, client.getSkillExperience(skill));
			}
			for (Varbits diary : varbitsAchievementDiaries) {
				int value = client.getVar(diary);
				oldAchievementDiaries.put(diary, value);
			}
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		switch(event.getGameState())
		{
			case LOGIN_SCREEN:
			case HOPPING:
			case LOGGING_IN:
			case LOGIN_SCREEN_AUTHENTICATOR:
				oldExperience.clear();
				oldAchievementDiaries.clear();
			case CONNECTION_LOST:
				resetTicks = true;
				// set to 0 here in-case of race condition with varbits changing before this handler is called
				// when game state becomes LOGGED_IN
				ticksSinceLogin = 0;
				break;
			case LOGGED_IN:
				if (resetTicks) {
					resetTicks = false;
					ticksSinceLogin = 0;
				}
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		ticksSinceLogin++;
	}

	@Subscribe
	public void onStatChanged(StatChanged statChanged) {
		final Skill skill = statChanged.getSkill();

		// Modified from Nightfirecat's virtual level ups plugin as this info isn't (yet?) built in to statChanged event
		final int xpAfter = client.getSkillExperience(skill);
		final int levelAfter = Experience.getLevelForXp(xpAfter);
		final int xpBefore = oldExperience.getOrDefault(skill, -1);
		final int levelBefore = xpBefore == -1 ? -1 : Experience.getLevelForXp(xpBefore);

		oldExperience.put(skill, xpAfter);

		// Do not proceed if any of the following are true:
		//  * xpBefore == -1              (don't fire when first setting new known value)
		//  * xpAfter <= xpBefore         (do not allow 200m -> 200m exp drops)
		//  * levelBefore >= levelAfter   (stop if if we're not actually reaching a new level)
		//  * levelAfter > MAX_REAL_LEVEL && config says don't include virtual (level is virtual and config ignores virtual)
		if (xpBefore == -1 || xpAfter <= xpBefore || levelBefore >= levelAfter ||
				(levelAfter > Experience.MAX_REAL_LEVEL && !config.announceLevelUpIncludesVirtual())) {
			return;
		}

		// If we get here, 'skill' was leveled up!
		if (config.announceLevelUp()) {
			soundEngine.playClip(randomSound(sounds));
		}
	}

	@Subscribe
	public void onActorDeath(ActorDeath actorDeath) {
		if (config.announceDeath() && actorDeath.getActor() == client.getLocalPlayer()) {
			soundEngine.playClip(randomSound(deathSounds));
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage chatMessage) {
		if (chatMessage.getType() != ChatMessageType.GAMEMESSAGE && chatMessage.getType() != ChatMessageType.SPAM) {
			return;
		}

		if (config.announceCollectionLog() && COLLECTION_LOG_ITEM_REGEX.matcher(chatMessage.getMessage()).matches()) {
			soundEngine.playClip(randomSound(sounds));
		} else if (config.announceQuestCompletion() && QUEST_REGEX.matcher(chatMessage.getMessage()).matches()) {
			soundEngine.playClip(randomSound(sounds));
		} else if (config.announceCombatAchievement() && COMBAT_TASK_REGEX.matcher(chatMessage.getMessage()).matches()) {
			soundEngine.playClip(randomSound(sounds));
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged varbitChanged) {
		// As we can't listen to specific varbits, we get a tonne of events BEFORE the game has even set the player's
		// diary varbits correctly, meaning it assumes every diary is on 0, then suddenly every diary that has been
		// completed gets updated to the true value and tricks the plugin into thinking they only just finished it.
		// To avoid this behaviour, we make sure the current tick count is sufficiently high that we've already passed
		// the initial wave of varbit changes from logging in.
		if (ticksSinceLogin < 8) {
			return;
		}

		// Apparently I can't check if it's a particular varbit using the names from Varbits enum, so this is the way
		for (Varbits diary : varbitsAchievementDiaries) {
			int newValue = client.getVar(diary);
			int previousValue = oldAchievementDiaries.getOrDefault(diary, -1);
			oldAchievementDiaries.put(diary, newValue);
			if (config.announceAchievementDiary() && previousValue != -1 && previousValue != newValue && isAchievementDiaryCompleted(diary, newValue)) {
				// value was not unknown (we know the previous value), value has changed, and value indicates diary is completed now
				soundEngine.playClip(randomSound(sounds));
			}
		}
	}

	private boolean isAchievementDiaryCompleted(Varbits diary, int value) {
		switch (diary) {
			case DIARY_KARAMJA_EASY:
			case DIARY_KARAMJA_MEDIUM:
			case DIARY_KARAMJA_HARD:
				return value == 2; // jagex, why?
			default:
				return value == 1;
		}
	}
	@Subscribe
	public void onOverheadTextChanged(OverheadTextChanged event) {
		if (event.getOverheadText().toLowerCase().contains("death")) {
			soundEngine.playClip(randomSound(deathSounds));
		}
		if (event.getOverheadText().toLowerCase().contains("lvl up")) {
			soundEngine.playClip(randomSound(sounds));
		}
		if (event.getOverheadText().toLowerCase().contains("big")) {
			soundEngine.playClip(randomSound(longSounds));
		}
	}

	@Provides
	CEngineerCompletedConfig provideConfig(ConfigManager configManager)
	{
		   return configManager.getConfig(CEngineerCompletedConfig.class);
	}
}
