package org.breedinginsight.model;

//Mirrors Pedigree.ts in bi-web to enable pedigree parsing on backend
public class Pedigree {
    public String femaleParent;
    public String maleParent;

    public Pedigree(String femaleParent, String maleParent){
        this.femaleParent = femaleParent;
        this.maleParent = maleParent;
    }

    /**
     * Pedigree string format from bi-api is femaleParentGID/maleParentGID.
     * It's possible to have only a female parent, but not only a male.
     */
    public static Pedigree parsePedigreeString(String pedigreeString) {
        if (pedigreeString.isEmpty()) {
            return new Pedigree("","");
        }
        String[] parents = pedigreeString.split("/", 2);
        if (parents.length == 2) {
            return new Pedigree(parents[0], parents[1]);
        } else if (parents.length == 1) {
            return new Pedigree(parents[0], "");
        } else {
            return new Pedigree("", "");
        }
    }
}
