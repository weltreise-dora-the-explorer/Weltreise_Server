package at.aau.serg.websocketdemoserver.game.minigame;

import java.util.List;
import java.util.Random;

public class GuessQuestionPool {
    private static final List<GuessQuestion> QUESTIONS = List.of(
        new GuessQuestion(1,  "Wie viele Einwohner hat Tokio? (in Millionen, gerundet)", 14),
        new GuessQuestion(2,  "Wie lang ist der Nil in Kilometern?", 6650),
        new GuessQuestion(3,  "Auf welcher Höhe liegt der Mount Everest in Metern?", 8849),
        new GuessQuestion(4,  "Wie groß ist die Fläche Australiens in km²?", 7692024),
        new GuessQuestion(5,  "Wie viele Länder hat Afrika?", 54),
        new GuessQuestion(6,  "Wie lang ist der Amazonas in Kilometern?", 6400),
        new GuessQuestion(7,  "Wie tief ist der Marianengraben in Metern?", 11034),
        new GuessQuestion(8,  "Wie viele Einwohner hat Indien (in Millionen, gerundet)?", 1400),
        new GuessQuestion(9,  "Wie viele km entfernt ist der Mond von der Erde?", 384400),
        new GuessQuestion(10, "Wie viele Länder liegen in Europa?", 44),
        new GuessQuestion(11, "Wie hoch ist der Eiffelturm in Metern?", 330),
        new GuessQuestion(12, "In welchem Jahr wurde der Vatikan gegründet?", 1929),
        new GuessQuestion(13, "Wie hoch ist der Großglockner in Metern?", 3798),
        new GuessQuestion(14, "Wie viele Bundesländer hat Deutschland?", 16),
        new GuessQuestion(15, "Wie viele Einwohner hat die USA (in Millionen, gerundet)?", 335),
        new GuessQuestion(16, "Wie lang ist die Donau in Kilometern?", 2860),
        new GuessQuestion(17, "Wie hoch ist der Stephansdom in Wien in Metern?", 136),
        new GuessQuestion(18, "Wie viele Einwohner hat Wien (in Millionen, gerundet)?", 2),
        new GuessQuestion(19, "Wie lang ist die Chinesische Mauer in Kilometern?", 21196),
        new GuessQuestion(20, "In welchem Jahr wurde die Titanic versenkt?", 1912),
        new GuessQuestion(21, "Wie viele Stufen hat der Eiffelturm bis zur ersten Etage?", 328),
        new GuessQuestion(22, "Wie hoch ist der Kilimandscharo in Metern?", 5895),
        new GuessQuestion(23, "Wie tief ist der Bodensee in Metern (maximale Tiefe)?", 254),
        new GuessQuestion(24, "Wie viele Einwohner hat China (in Milliarden, gerundet)?", 1),
        new GuessQuestion(25, "Wie lang ist der Rhein in Kilometern?", 1230),
        new GuessQuestion(26, "In welchem Jahr wurde die Berliner Mauer gebaut?", 1961),
        new GuessQuestion(27, "Wie viele Länder hat die Europäische Union?", 27),
        new GuessQuestion(28, "Wie groß ist die Fläche Russlands in Millionen km²?", 17),
        new GuessQuestion(29, "Wie hoch ist der Burj Khalifa in Dubai in Metern?", 828),
        new GuessQuestion(30, "In welchem Jahr landete der erste Mensch auf dem Mond?", 1969),
        new GuessQuestion(31, "Wie viele Einwohner hat Österreich (in Millionen, gerundet)?", 9),
        new GuessQuestion(32, "Wie lang ist der Mississippi in Kilometern?", 3730),
        new GuessQuestion(33, "Wie viele Weltwunder der Antike gibt es?", 7),
        new GuessQuestion(34, "Wie hoch ist der Mont Blanc in Metern?", 4808),
        new GuessQuestion(35, "Wie viele Länder liegen in Südamerika?", 12),
        new GuessQuestion(36, "In welchem Jahr wurde die EU gegründet?", 1993),
        new GuessQuestion(37, "Wie groß ist die Sahara in Millionen km²?", 9),
        new GuessQuestion(38, "Wie viele Zeitzonen hat Russland?", 11),
        new GuessQuestion(39, "Wie tief ist der Titicacasee in Metern (maximale Tiefe)?", 281),
        new GuessQuestion(40, "Wie hoch ist der Vesuvio (Vesuv) in Metern?", 1281),
        new GuessQuestion(41, "Wie lang ist die Transsibirische Eisenbahn in Kilometern?", 9289),
        new GuessQuestion(42, "In welchem Jahr wurde das Kolosseum in Rom fertiggestellt?", 80),
        new GuessQuestion(43, "Wie viele Einwohner hat Brasilien (in Millionen, gerundet)?", 215),
        new GuessQuestion(44, "Wie hoch ist die Freiheitsstatue in New York in Metern?", 93),
        new GuessQuestion(45, "Wie groß ist die Antarktis in Millionen km²?", 14),
        new GuessQuestion(46, "Wie viele Inseln hat Griechenland?", 6000),
        new GuessQuestion(47, "Wie lang ist der Suezkanal in Kilometern?", 193),
        new GuessQuestion(48, "In welchem Jahr begann der Zweite Weltkrieg?", 1939),
        new GuessQuestion(49, "Wie viele Länder grenzen an Deutschland?", 9),
        new GuessQuestion(50, "Wie hoch ist der Olymp in Griechenland in Metern?", 2918),
        new GuessQuestion(51, "Wie lang ist die Alpenhauptkette in Kilometern?", 1200),
        new GuessQuestion(52, "In welchem Jahr wurde das Brandenburger Tor gebaut?", 1791),
        new GuessQuestion(53, "Wie viele Bundesstaaten hat die USA?", 50),
        new GuessQuestion(54, "Wie lang ist der Panamakanal in Kilometern?", 82),
        new GuessQuestion(55, "Wie hoch ist der Ätna auf Sizilien in Metern?", 3357),
        new GuessQuestion(56, "Wie viele Einwohner hat Australien (in Millionen, gerundet)?", 26),
        new GuessQuestion(57, "In welchem Jahr wurde der Eiffelturm erbaut?", 1889),
        new GuessQuestion(58, "Wie groß ist die Fläche Brasiliens in Millionen km²?", 8),
        new GuessQuestion(59, "Wie viele Sprachen sind in der EU offiziell anerkannt?", 24),
        new GuessQuestion(60, "Wie hoch liegt Salzburg über dem Meeresspiegel in Metern?", 424)

    );

    private final Random random = new Random();

    public GuessQuestion getRandom() {
        return QUESTIONS.get(random.nextInt(QUESTIONS.size()));
    }
}
