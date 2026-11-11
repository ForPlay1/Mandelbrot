package fractalcipher.model.permutation;


import java.util.Arrays;
import java.util.Map;
import java.util.Random;
import java.util.stream.IntStream;

/**
 * Реализация источника перестановки на основе фрактала "Burning Ship" (Горящий корабль).
 * <p>
 * Математическая формула фрактала:
 * {@code z_{n+1} = (|Re(z_n)| + i·|Im(z_n)|)^2 + c}
 * </p>
 * <p>
 * Этот фрактал является гибридом Мандельброта и Жюлиа:
 * <ul>
 *   <li>{@code c} — фиксированный параметр для всего изображения (как у Жюлиа),
 *       который генерируется из переданного {@code seed}.</li>
 *   <li>{@code z_0} — координата текущего пикселя на комплексной плоскости.</li>
 *   <li>Перед возведением в квадрат берётся модуль действительной и мнимой части,
 *       именно это даёт характерную форму "горящего корабля".</li>
 * </ul>
 * </p>
 * <p>
 * Для каждого пикселя вычисляется "escape-time" (количество итераций до выхода за границу),
 * который затем используется как вес для сортировки. Порядок сортировки
 * определяет новую позицию пикселя при перестановке.
 * </p>
 *
 * @see PermutationSource
 * @see MandelbrotPermutation
 * @see JuliaPermutation
 */
public class BurningShipPermutation implements PermutationSource {
    /**
     * Максимальное количество итераций по умолчанию.
     * Чем больше это значение, тем точнее определяется граница фрактала,
     * но тем дольше выполняется вычисление.
     */
    private static final int DEFAULT_MAX_ITERATIONS = 300;

    /**
     * Радиус убегания. Если модуль комплексного числа превышает это значение,
     * считается, что точка "улетела" в бесконечность (не принадлежит множеству).
     * Стандартное значение — 2.0.
     */
    private static final double ESCAPE_RADIUS = 2.0;
    /**
     * Генерирует перестановку пикселей на основе фрактала Burning Ship.
     * <p>
     * Метод разворачивает одномерный массив пикселей в двумерную сетку,
     * сопоставляет каждому пикселю точку на комплексной плоскости {@code z_0},
     * вычисляет для неё escape-time и сортирует индексы по полученным значениям.
     * </p>
     *
     * @param length Общее количество пикселей в изображении (ширина × высота).
     * @param params Карта параметров, содержащая:
     *               <ul>
     *                 <li>{@code width} — ширина изображения (обязательно).</li>
     *                 <li>{@code seed} — зерно для генерации параметров {@code c} и области.</li>
     *                 <li>{@code reMin, reMax, imMin, imMax} — границы комплексной плоскости.</li>
     *                 <li>{@code cRe, cIm} — фиксированная константа {@code c}.</li>
     *                 <li>{@code maxIter} — максимальное число итераций.</li>
     *                 <li>{@code isParallel} — флаг (1.0 - включить параллелизм, 0.0 - выключить).</li>
     *               </ul>
     * @return Массив целых чисел {@code int[]}, представляющий собой перестановку.
     *         Значение {@code result[i]} — это индекс пикселя, который должен встать на позицию {@code i}.
     */
    @Override
    public int[] generatePermutation(int length, Map<String, Double> params) {
        // Извлекаем ширину изображения (по умолчанию считаем, что изображение в одну строку)
        int width = params.getOrDefault("width", (double) length).intValue();
        // Вычисляем высоту, защищаясь от деления на ноль
        int height = Math.max(1, length / width);
        // Извлекаем seed для детерминированной генерации случайных параметров
        long seed = params.getOrDefault("seed", 0.0).longValue();
        Random rnd = new Random(seed);

        // === Область комплексной плоскости для z_0 ===
        // Burning Ship "живёт" в области примерно [-2.5, 1] × [-2, 1].
        // Если параметры не переданы, генерируем их на основе seed с небольшим разбросом.
        double reMin = params.getOrDefault("reMin", -2.5 - rnd.nextDouble() * 0.5);
        double reMax = params.getOrDefault("reMax",  1.0 + rnd.nextDouble() * 0.5);
        double imMin = params.getOrDefault("imMin", -2.0 - rnd.nextDouble() * 0.5);
        double imMax = params.getOrDefault("imMax",  1.0 + rnd.nextDouble() * 0.5);

        // === Фиксированный параметр c ===
        // Классический Burning Ship использует c = -1.8 + 0.0i.
        // Генерируем уникальное c из seed для каждого сеанса шифрования.
        double cRe = params.getOrDefault("cRe", -1.8 + rnd.nextDouble() * 1.0);
        double cIm = params.getOrDefault("cIm", -0.1 + rnd.nextDouble() * 0.3);

        // Максимальное число итераций (если не задано, берем дефолтное + случайный разброс)
        int maxIter = params.getOrDefault("maxIter",
                (double) (DEFAULT_MAX_ITERATIONS + rnd.nextInt(700))).intValue();

        // Флаг контекстного параллелизма (по умолчанию 0.0 — последовательно)
        boolean isParallel = params.getOrDefault("isParallel",0.0) == 1.0;

        // Массив "оценок" (весов) — по одной на каждый пиксель
        double[] values = new double[length];

        // Финализируем переменные для использования внутри лямбда-выражения
        final int fWidth = width;
        final int fHeight = height;
        final double fReMin = reMin, fReMax = reMax, fImMin = imMin, fImMax = imMax;
        final int fMaxIter = maxIter;
        final double fCRe = cRe, fCIm = cIm;

        // Создаем поток индексов от 0 до length-1
        IntStream stream = IntStream.range(1,length);
        // Если включен параллелизм, переводим поток в параллельный режим
        if(isParallel) {
            stream = stream.parallel();
        }
        // Вычисляем вес для каждого пикселя
        stream.forEach(i-> {
            int row = i / fWidth;
            int col = i % fWidth;
            // Преобразуем координаты пикселя в точку на комплексной плоскости
            double zRe0 = fReMin + (fReMax - fReMin) * col / fWidth;
            double zIm0 = fImMin + (fImMax - fImMin) * row / fHeight;
            // Вычисляем escape-time для данной точки
            values[i] = smoothEscapeTimeBurningShip(zRe0, zIm0, fCRe, fCIm, fMaxIter);
        });
        // Превращаем массив весов в перестановку индексов
        return sortIndicesByValue(values,isParallel);
    }

    /**
     * Вычисляет сглаженное количество итераций (smooth iteration count) для фрактала Burning Ship.
     * <p>
     * В отличие от Мандельброта и Жюлиа, здесь перед возведением в квадрат
     * берётся модуль действительной и мнимой частей: {@code (|Re(z)| + i·|Im(z)|)^2 + c}.
     * </p>
     *
     * @param zRe     Начальная действительная часть точки (координата пикселя).
     * @param zIm     Начальная мнимая часть точки (координата пикселя).
     * @param cRe     Действительная часть фиксированной константы {@code c}.
     * @param cIm     Мнимая часть фиксированной константы {@code c}.
     * @param maxIter Максимальное количество итераций.
     * @return Дробное значение escape-time. Если точка не убежала, возвращает {@code maxIter}.
     */
    private double smoothEscapeTimeBurningShip(double zRe, double zIm, double cRe, double cIm, int maxIter) {
        int iterations = 0;
        double radiusSquared = ESCAPE_RADIUS * ESCAPE_RADIUS;

        while (iterations < maxIter) {
            double zRe2 = zRe * zRe;
            double zIm2 = zIm * zIm;

            // Проверяем, не вышла ли точка за пределы радиуса убегания
            if (zRe2 + zIm2 > radiusSquared) {
                break;
            }

            // === ГЛАВНОЕ ОТЛИЧИЕ BURNING SHIP ===
            // Берем модуль (абсолютную величину) действительной и мнимой частей
            double absZRe = Math.abs(zRe);
            double absZIm = Math.abs(zIm);

            // Возводим модули в квадрат
            double absZRe2 = absZRe * absZRe;
            double absZIm2 = absZIm * absZIm;

            // Вычисляем следующую итерацию: (|Re(z)| + i·|Im(z)|)^2 + c
            // (a+bi)^2 = (a^2 - b^2) + 2abi
            double nextZRe = absZRe2 - absZIm2 + cRe;
            double nextZIm = 2.0 * absZRe * absZIm + cIm;

            // Обновляем состояние
            zRe = nextZRe;
            zIm = nextZIm;
            iterations++;
        }

        // === Smooth Iteration Count ===
        // Если точка "улетела", вычисляем сглаженное значение для устранения "полос"
        if (iterations < maxIter) {
            double magSq = zRe * zRe + zIm * zIm;
            double absZ = Math.sqrt(magSq);
            if (absZ > 1.0) {
                double logZn = Math.log(absZ);
                double nu = Math.log(logZn / Math.log(2.0)) / Math.log(2.0);
                return iterations + 1.0 - nu;
            }
        }
        // Точка внутри множества — возвращаем максимальное число итераций
        return iterations;
    }

    /**
     * Превращает массив весов в массив перестановок индексов.
     * <p>
     * Используется оптимизированный трюк: значения типа {@code double} упаковываются
     * в старшие 32 бита типа {@code long}, а индекс — в младшие 32 бита.
     * Это позволяет использовать быструю примитивную сортировку {@link Arrays#sort(long[])}
     * без накладных расходов на boxing (создание объектов {@link Double}).
     * </p>
     *
     * @param values     Массив весов (результатов escape-time).
     * @param isParallel Флаг, использовать ли параллельную сортировку.
     * @return Массив индексов, отсортированных по возрастанию соответствующих им весов.
     */
    private int[] sortIndicesByValue(double[] values,boolean isParallel) {
        int n = values.length;

        // Массив для упакованных значений: [64 бита значения][32 бита индекса]
        long[] packed = new long[n];
        for (int i = 0; i < n; i++) {
            // Получаем битовое представление double
            long bits = Double.doubleToLongBits(values[i]);

            // Если число отрицательное (старший бит 1), инвертируем все биты,
            // чтобы отрицательные числа шли перед положительными при сортировке
            if (bits < 0) bits ^= 0x7FFFFFFFFFFFFFFFL;
            // Склеиваем: старшие 32 бита от bits, младшие 32 бита от индекса i
            packed[i] = (bits & 0xFFFFFFFF00000000L) | (i & 0xFFFFFFFFL);
        }

        // Сортируем упакованный массив
        if(isParallel) {
            Arrays.parallelSort(packed);
        }
        else{
            Arrays.sort(packed);
        }
        // Распаковываем результат — извлекаем младшие 32 бита (индексы)
        int[] result = new int[n];
        for (int i = 0; i < n; i++) {
            result[i] = (int)(packed[i] & 0xFFFFFFFFL);
        }
        return result;
    }

    /**
     * Возвращает имя источника перестановки для регистрации в фабрике.
     *
     * @return Строка "burning ship".
     */
    @Override
    public String getName() {
        return "burning ship";
    }
}
