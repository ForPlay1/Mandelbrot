package fractalcipher.model.permutation;

import java.util.Map;

// Временный класс IdentityPermutation
public class IdentityPermutation implements PermutationSource {
    @Override
    public int[] generatePermutation(int length, Map<String, Double> params) {
        int[] perm = new int[length];
        for (int i = 0; i < length; i++) perm[i] = i;
        return perm;
    }
    @Override
    public String getName() { return "identity"; }
}