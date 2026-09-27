package fractalcipher.model;

import fractalcipher.model.diffusion.DiffusionSource;
import fractalcipher.model.diffusion.DiffusionSourceFactory;
import fractalcipher.model.permutation.PermutationSource;
import fractalcipher.model.permutation.PermutationSourceFactory;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

// Единственный класс, который реально "склеивает" перестановку и
// диффузию в рабочий шифр. Сам не содержит ни одной формулы фрактала
// или карты — только вызывает их через интерфейсы и умеет применять/
// откатывать перестановку и XOR к массиву пикселей.
public class ImageCipherImpl implements ImageCipher {

    // Через фабрики достаём нужный источник по имени из ключа —
    // сам ImageCipherImpl не импортирует ни один конкретный фрактал.
    private final PermutationSourceFactory permutationFactory;
    private final DiffusionSourceFactory diffusionFactory;

    public ImageCipherImpl(PermutationSourceFactory permutationFactory, DiffusionSourceFactory diffusionFactory) {
        this.permutationFactory = permutationFactory;
        this.diffusionFactory = diffusionFactory;
    }

    @Override
    public BufferedImage encrypt(BufferedImage image, EncryptionKey key) {
        int width = image.getWidth();
        int height = image.getHeight();

        // getRGB(...) достаёт все пиксели одним массивом int[], где
        // каждый int — это упакованный ARGB-цвет одного пикселя
        // (8 бит альфа, 8 бит красный, 8 бит зелёный, 8 бит синий).
        // Это быстрее, чем ходить по пикселям через getRGB(x,y) в цикле.
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);

        // Шаг 1: перестановка (confusion) — меняем МЕСТА пикселей,
        // сами цвета пока не трогаем.
        int[] permutation = getPermutation(width, height, key);
        int[] scrambled = applyPermutation(pixels, permutation);

        // Шаг 2: диффузия — меняем САМИ ЦВЕТА через XOR с потоком байт.
        // Нужно 3 байта на пиксель (по одному на R, G, B — альфу не трогаем).
        byte[] stream = getDiffusionStream(pixels.length * 3, key);
        int[] diffused = applyDiffusion(scrambled, stream);

        return toImage(diffused, width, height);
    }

    @Override
    public BufferedImage decrypt(BufferedImage image, EncryptionKey key) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);

        // Расшифровка идёт СТРОГО в обратном порядке относительно encrypt:
        // сначала откатываем диффузию, потом перестановку.
        //
        // Откат XOR — это снова XOR с тем же самым потоком: XOR — операция,
        // обратная сама себе (a ^ b ^ b = a), поэтому отдельного метода
        // "undoDiffusion" не нужно, applyDiffusion делает и шифрование,
        // и расшифровку.
        byte[] stream = getDiffusionStream(pixels.length * 3, key);
        int[] unDiffused = applyDiffusion(pixels, stream);

        // А вот перестановку так просто не откатить — нужна ОБРАТНАЯ
        // перестановка (см. invertPermutation).
        int[] permutation = getPermutation(width, height, key);
        int[] inverse = invertPermutation(permutation);
        int[] original = applyPermutation(unDiffused, inverse);

        return toImage(original, width, height);
    }

    // Получить готовую перестановку для текущего изображения и ключа.
    // Обратите внимание: width/height добавляются в params ЗДЕСЬ,
    // а не в EncryptionKey — потому что размер зависит от конкретного
    // изображения, а не от ключа (один и тот же ключ может применяться
    // к картинкам разного размера).
    private int[] getPermutation(int width, int height, EncryptionKey key) {
        PermutationSource source = permutationFactory.get(key.permutationSourceName);

        // Копируем params из ключа (new HashMap<>(...)), чтобы не менять
        // оригинальную Map из EncryptionKey — добавление width/height
        // не должно "портить" ключ для повторного использования.
        Map<String, Double> params = new HashMap<>(key.permutationParams);
        params.put("width", (double) width);
        params.put("height", (double) height);

        return source.generatePermutation(width * height, params);
    }

    private byte[] getDiffusionStream(int length, EncryptionKey key) {
        DiffusionSource source = diffusionFactory.get(key.diffusionSourceName);
        return source.generateStream(length, key.diffusionParams);
    }

    // Применяет перестановку: пиксель, который был на месте permutation[i],
    // переезжает на новое место i.
    // Пример: pixels = [A, B, C], permutation = [2, 0, 1]
    // -> result[0] = pixels[2] = C
    // -> result[1] = pixels[0] = A
    // -> result[2] = pixels[1] = B
    // -> result = [C, A, B]
    private int[] applyPermutation(int[] pixels, int[] permutation) {
        int[] result = new int[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            result[i] = pixels[permutation[i]];
        }
        return result;
    }

    // Строит перестановку, обратную данной. Если permutation[i] = j
    // (то есть "пиксель j встал на место i"), то inverse[j] = i
    // (то есть "чтобы вернуть пиксель на место j, нужно взять то,
    // что сейчас стоит на месте i"). Продолжая пример выше:
    // permutation = [2, 0, 1] -> inverse = [1, 2, 0]
    // Проверка: applyPermutation([C, A, B], [1, 2, 0])
    // -> result[0] = scrambled[1] = A
    // -> result[1] = scrambled[2] = B
    // -> result[2] = scrambled[0] = C
    // -> result = [A, B, C] — вернули исходный порядок.
    private int[] invertPermutation(int[] permutation) {
        int[] inverse = new int[permutation.length];
        for (int i = 0; i < permutation.length; i++) {
            inverse[permutation[i]] = i;
        }
        return inverse;
    }

    // Применяет диффузию: разбирает каждый int-пиксель на 4 канала
    // (alpha, red, green, blue), XOR'ит r/g/b с байтами из потока
    // (альфу не трогаем — она отвечает за прозрачность, не за цвет,
    // и её порча не нужна), собирает пиксель обратно.
    private int[] applyDiffusion(int[] pixels, byte[] stream) {
        int[] result = new int[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            int argb = pixels[i];

            // Распаковка ARGB: каждый канал занимает свои 8 бит.
            // >> сдвигает нужные биты в младший байт, & 0xFF обрезает
            // всё лишнее слева, оставляя только один байт (0..255).
            int a = (argb >> 24) & 0xFF;
            int r = (argb >> 16) & 0xFF;
            int g = (argb >> 8) & 0xFF;
            int b = argb & 0xFF;

            // Для пикселя i используем 3 последовательных байта потока:
            // stream[i*3] для R, stream[i*3+1] для G, stream[i*3+2] для B.
            // stream[...] & 0xFF нужен, потому что byte в Java знаковый
            // (-128..127), а нам нужно беззнаковое значение (0..255)
            // для корректного XOR с r/g/b.
            r ^= stream[i * 3] & 0xFF;
            g ^= stream[i * 3 + 1] & 0xFF;
            b ^= stream[i * 3 + 2] & 0xFF;

            // Собираем ARGB обратно: сдвигаем каждый канал на своё
            // место и объединяем через побитовое ИЛИ.
            result[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }
        return result;
    }

    // Обратная операция к image.getRGB(...) — собирает BufferedImage
    // из плоского массива int[] пикселей.
    private BufferedImage toImage(int[] pixels, int width, int height) {
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        result.setRGB(0, 0, width, height, pixels, 0, width);
        return result;
    }
}