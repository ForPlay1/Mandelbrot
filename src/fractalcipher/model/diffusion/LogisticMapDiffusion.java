package fractalcipher.model.diffusion;

import java.util.Map;
import java.security.SecureRandom;

// Рабочая (не заглушка) реализация. Логистическая карта —
// классический пример детерминированного хаоса, при этом считается
// очень быстро, поэтому и выбрана как первый источник диффузии.
public class LogisticMapDiffusion implements DiffusionSource {

    @Override
    public byte[] generateStream(int length, Map<String, Double> params) {
        // r — параметр карты. При r около 3.57-4.0 карта ведёт себя
        // хаотично (это нам и нужно); 3.99 — стандартное "надёжно
        // хаотичное" значение по умолчанию.
        double r = params.getOrDefault("r", 3.99);

        SecureRandom rnd = new SecureRandom();
        long bits;
        do {
            bits = rnd.nextLong() >>> 11;     // [0, 2^53)
        } while (bits == 0);

        double chaos = bits / (double) (1L << 53); // (0.0, 1.0)

        // x0 — стартовое значение, часть ключа. Должно быть в (0,1),
        // иначе последовательность быстро выродится в 0 или разойдётся.
        double x = params.getOrDefault("x0", chaos);

        byte[] stream = new byte[length];
        for (int i = 0; i < length; i++) {
            // Сама формула логистической карты:
            // x_{n+1} = r * x_n * (1 - x_n)
            // При выбранном r значение x после каждой итерации скачет
            // почти непредсказуемо, оставаясь в диапазоне (0,1).
            x = r * x * (1 - x);

            // x лежит в (0,1) — растягиваем его в диапазон байта (0..255).
            // (int)(x * 256) может изредка дать 256 при x очень близком
            // к 1 — & 0xFF на такой случай обрежет лишний бит и оставит
            // корректный байт (защита от переполнения на границе).
            stream[i] = (byte) (((int) (x * 256)) & 0xFF);
        }
        return stream;
    }

    @Override
    public String getName() {
        return "logistic";
    }
}