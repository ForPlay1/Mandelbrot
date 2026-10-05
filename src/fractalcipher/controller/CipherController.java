package fractalcipher.controller;

import fractalcipher.domain.Cipher;
import fractalcipher.domain.Image;
import fractalcipher.metrics.BenchmarkResult;
import fractalcipher.metrics.BenchmarkRunner;
import fractalcipher.model.diffusion.DiffusionSourceFactory;
import fractalcipher.model.permutation.PermutationSourceFactory;

import java.util.List;

public class CipherController {

    public Image handleEncrypt(Cipher cipher, Image input) {
        cipher.setImageInput(input);
        cipher.encrypt();
        return cipher.getImageOutput();
    }

    public Image handleDecrypt(Cipher cipher, Image input) {
        cipher.setImageInput(input);
        cipher.decrypt();
        return cipher.getImageOutput();
    }

    public List<BenchmarkResult> handleBenchmark(PermutationSourceFactory permutationFactory,
                                                 DiffusionSourceFactory diffusionFactory,
                                                 Image image) {
        return BenchmarkRunner.run(permutationFactory, diffusionFactory, image);
    }
}