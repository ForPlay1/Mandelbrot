package fractalcipher.model.permutation;

import java.util.Map;

// Заглушка. Множество Жюлиа считается той же формулой z = z^2+c,
// что и Мандельброт, но с точностью до наоборот: c — фиксированный
// параметр ключа (одно и то же для всех пикселей), а по изображению
// "бегает" именно z0 (стартовая точка), а не c.
// Не решено: как именно превращать (row, col) в z0 — TODO для ЛР.
public class JuliaPermutation implements PermutationSource {

    @Override
    public int[] generatePermutation(int length, Map<String, Double> params) {
        // Пока просто честно говорим, что не готово, вместо того
        // чтобы возвращать "притворную" перестановку.
        throw new UnsupportedOperationException("Перестановка на основе Жюлиа ещё не реализована");
    }

    @Override
    public String getName() {
        return "julia";
    }
}