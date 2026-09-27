package fractalcipher.model;

// Простое комплексное число re + im*i.
// Нужно и для перестановки (MandelbrotPermutation), и в будущем
// для других фракталов на основе итераций z = z^2 + c.
public class Complex {
    public final double re; // действительная часть
    public final double im; // мнимая часть

    public Complex(double re, double im) {
        this.re = re;
        this.im = im;
    }

    // Сложение комплексных чисел: (a+bi) + (c+di) = (a+c) + (b+d)i
    public Complex add(Complex other) {
        return new Complex(this.re + other.re, this.im + other.im);
    }

    // Умножение комплексных чисел по формуле:
    // (a+bi)(c+di) = (ac - bd) + (ad + bc)i
    public Complex multiply(Complex other) {
        return new Complex(
                this.re * other.re - this.im * other.im, // новая действительная часть
                this.re * other.im + this.im * other.re  // новая мнимая часть
        );
    }

    // Квадрат модуля |z|^2 = re^2 + im^2.
    // Специально не берём корень (Math.sqrt) — для сравнения с
    // радиусом убегания достаточно квадрата, а без sqrt быстрее.
    public double absSquared() {
        return re * re + im * im;
    }
}