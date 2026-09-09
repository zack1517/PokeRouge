package org.example.model;

public class PokemonInstance {
    private Pokemon pokemon;

    public PokemonInstance() {
    }

    public PokemonInstance(Pokemon pokemon) {
        this.pokemon = pokemon;
    }

    public Pokemon getPokemon() {
        return pokemon;
    }

    public void setPokemon(Pokemon pokemon) {
        this.pokemon = pokemon;
    }

    public String getName() {
        return pokemon == null ? null : pokemon.getName();
    }

    public int getLevel() {
        return pokemon == null ? 0 : pokemon.getLevel();
    }

    public int getCurrentHp() {
        return pokemon == null ? 0 : pokemon.getCurrentHp();
    }

    public int getMaxHp() {
        return pokemon == null ? 0 : pokemon.getMaxHp();
    }

    public void setHp(int hp) {
        if (pokemon == null) {
            return;
        }
        int maxHp = getMaxHp();
        if (hp <= 0) {
            pokemon.takeDamage(maxHp);
            return;
        }
        if (hp >= maxHp) {
            pokemon.fullHeal();
            return;
        }
        int current = pokemon.getCurrentHp();
        int delta = hp - current;
        if (delta > 0) {
            pokemon.heal(delta);
        } else {
            pokemon.takeDamage(-delta);
        }
    }

    @Override
    public String toString() {
        return "PokemonInstance{" +
                "name='" + getName() + '\'' +
                ", level=" + getLevel() +
                ", hp=" + getCurrentHp() + "/" + getMaxHp() +
                '}';
    }
}
