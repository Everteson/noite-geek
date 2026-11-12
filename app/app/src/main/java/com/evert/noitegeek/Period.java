package com.evert.noitegeek;

import java.util.Calendar;

/** Período do dia: escolhe a arte, a saudação e o título do banner. */
final class Period {
    final String key;       // manha, tarde, noite, madrugada
    final String greeting;  // Bom dia...
    final String subtitle;  // linha embaixo da saudação
    final String word;      // "Noite" em "Noite Geek"
    final String banner;    // texto do banner
    final int variant;      // 1 ou 2: troca a arte a cada 90 minutos

    private Period(String key, String greeting, String subtitle, String word, String banner, int variant) {
        this.key = key;
        this.greeting = greeting;
        this.subtitle = subtitle;
        this.word = word;
        this.banner = banner;
        this.variant = variant;
    }

    static Period now() {
        Calendar c = Calendar.getInstance();
        int h = c.get(Calendar.HOUR_OF_DAY);
        int variant = ((h * 60 + c.get(Calendar.MINUTE)) / 90) % 2 + 1;
        if (h >= 5 && h < 12) {
            return new Period("manha", "Bom dia", "Café na mão e o dia começando",
                    "Manhã", "Animes, games, tecnologia e carros\npra começar o dia no seu ritmo.", variant);
        }
        if (h >= 12 && h < 18) {
            return new Period("tarde", "Boa tarde", "Hora de uma pausa: anime, jogo ou vídeo",
                    "Tarde", "Uma pausa merecida: escolha um episódio,\num vídeo ou uma partida.", variant);
        }
        if (h >= 18) {
            return new Period("noite", "Boa noite", "Seu espaço para relaxar, assistir e curtir",
                    "Noite", "Animes, games, tecnologia, carros e o universo\nem um só lugar. Dê o play na sua noite!", variant);
        }
        return new Period("madrugada", "Boa noite", "Madrugada adentro: só mais um episódio",
                "Madrugada", "O mundo dormiu, a fila de episódios não.\nVolume baixo e boa sessão.", variant);
    }

    String artName() {
        return key + "-" + variant;
    }

    boolean sameArt(Period o) {
        return o != null && o.artName().equals(artName());
    }
}
