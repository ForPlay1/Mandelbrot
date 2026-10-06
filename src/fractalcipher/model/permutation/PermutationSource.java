package fractalcipher.model.permutation;

import java.util.Map;

// Контракт для любого источника перестановки (confusion-часть шифра).
// Реализация не обязана быть фракталом в узком смысле — важно только,
// что на выходе получается перестановка индексов пикселей.
public interface PermutationSource {

    // length      — сколько пикселей нужно переставить (width * height)
    // params      — параметры конкретного фрактала (свои для каждого),
    //               сюда же FractalChaosCipher всегда добавляет "width" и "height",
    //               чтобы источник знал форму изображения, а не только длину массива
    //
    // Возвращает массив permutation длиной length, где
    // permutation[новая_позиция] = исходный_индекс_пикселя.
    // Именно так работает MandelbrotPermutation: генерируется double[]
    // нужной длины, массив сортируется, и порядок индексов после
    // сортировки и есть эта перестановка.
    int[] generatePermutation(int length, Map<String, Double> params);

    // Имя для регистрации в PermutationSourceFactory и для выбора
    // пользователем/бенчмарком, например "mandelbrot"
    String getName();
}