package com.thuvstu.hayatemod.azemichi;

/**
 * The kinds of encounters that can be placed along the azemichi, after the
 * characters of the original game.
 *
 * <p>Each type carries a translation key base: {@code <base>.name} is the marker's
 * custom name, {@code <base>.question} the question, and {@code <base>.choice.N}
 * the individual answer options. Types with {@link #choiceCount()} of zero act on
 * a plain right-click (the scarecrow, the old lady).
 */
public enum EnenEventType {
	/** The vending machine: pay 100 / 1000 / 10000 (emeralds) for a surprise. */
	VENDING("enen.hayatemod.event.vending", 3),
	/** The ringing phone booth: listen to the end, or hang up. */
	PHONE("enen.hayatemod.event.phone", 2),
	/** The old lady with the bread. Something hides in her face after a while. */
	OLD_LADY("enen.hayatemod.event.old_lady", 0),
	/** A train rushes down the tracks: board it, or watch it pass. */
	TRAIN("enen.hayatemod.event.train", 2),
	/** The scarecrow: poke it to escape the azemichi. */
	SCARECROW("enen.hayatemod.event.scarecrow", 0),
	/** The dazed man: his one question moves the goal by a lot. */
	MONSTER("enen.hayatemod.event.monster", 3);

	private final String base;
	private final int choiceCount;

	EnenEventType(String base, int choiceCount) {
		this.base = base;
		this.choiceCount = choiceCount;
	}

	/** Translation key of the marker's custom name. */
	public String nameKey() {
		return this.base + ".name";
	}

	/** Translation key of the question shown when the player interacts. */
	public String questionKey() {
		return this.base + ".question";
	}

	/** Translation key of answer option {@code i} (zero based). */
	public String choiceKey(int i) {
		return this.base + ".choice." + i;
	}

	/** How many answers the player can pick; zero means "no choice, just do it". */
	public int choiceCount() {
		return this.choiceCount;
	}

	/** Round-trips the enum through its ordinal for entity save data. */
	public static EnenEventType byId(int id) {
		EnenEventType[] values = values();
		return id >= 0 && id < values.length ? values[id] : VENDING;
	}
}
