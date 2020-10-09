package io.github.unapplicable.hangman.service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.PrimitiveIterator;
import java.util.Random;
import java.util.stream.Collectors;

public class WordList {
    private final List<String> words;
    private final PrimitiveIterator.OfInt randomIterator;

    public WordList(InputStream stream) {
        this(new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
            .lines()
            .map(String::toLowerCase)
            .collect(Collectors.toList()));
    }

    public WordList(List<String> words) {
        this.words = words;
        randomIterator = new Random().ints(0, this.words.size()).iterator();
    }

    public String getRandom() {
        return words.get(randomIterator.nextInt());
    }
}
