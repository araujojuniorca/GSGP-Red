/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package edu.gsgp.population;

import edu.gsgp.Utils;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import edu.gsgp.nodes.Node;
import edu.gsgp.nodes.functions.Add;
import edu.gsgp.nodes.functions.Function;
import edu.gsgp.nodes.functions.Mul;
import edu.gsgp.nodes.terminals.ERC;
import edu.gsgp.population.fitness.Fitness;

/**
 * @author Luiz Otavio Vilas Boas Oliveira
 * http://homepages.dcc.ufmg.br/~luizvbo/ 
 * luiz.vbo@gmail.com
 * Copyright (C) 2014, Federal University of Minas Gerais, Belo Horizonte, Brazil
 */

public class GSGPIndividual extends Individual{

    private class LinearCombinationTerm {
        Node tree;
        double coefficient;

        public LinearCombinationTerm(Node tree, double coefficient) {
            this.tree = tree;
            this.coefficient = coefficient;
        }

        public LinearCombinationTerm copy(double newCoefficient){
            return new LinearCombinationTerm(this.tree, newCoefficient);
        }

        @Override
        public String toString() {
            return coefficient + " * " + tree;
        }
    }

    // Representation of the linear combination of functions
    private Map<String, LinearCombinationTerm> combinationRepr;

    public GSGPIndividual(Node tree, BigInteger numNodes, Fitness fitnessFunction) {
        super(tree, numNodes, fitnessFunction);
        combinationRepr = new HashMap<>();

        // Individual doesn't have an initial representation (probably from the initial population)
        combinationRepr.put(tree.toString(), new LinearCombinationTerm(tree, 1));
    }


    public GSGPIndividual(BigInteger numNodes, Fitness fitnessFunction, GSGPIndividual p1, GSGPIndividual p2, Double crossoverConst) {
        super(null, numNodes, fitnessFunction);
        combinationRepr = new HashMap<>();

        // Individual is a crossover offspring
        this.propagateCrossover(p1, p2, crossoverConst);
    }


    public GSGPIndividual(BigInteger numNodes, Fitness fitnessFunction, GSGPIndividual p1, Node mutationT1, Node mutationT2, double mutationStep) {
        super(null, numNodes, fitnessFunction);
        combinationRepr = new HashMap<>();

        // Individual is a mutation offspring
        this.propagateMutation(p1, mutationT1, mutationT2, mutationStep);
    }


    public GSGPIndividual(BigInteger numNodes, Fitness fitnessFunction, Map<String, LinearCombinationTerm> propagationRepr) {
        super(null, numNodes, fitnessFunction);

        // Individual has been cloned
        this.combinationRepr = propagationRepr;
    }


    @Override
    public GSGPIndividual clone(){
        if(tree != null)
            return new GSGPIndividual(tree.clone(null), numNodes, fitnessFunction.clone());
        return new GSGPIndividual(numNodes, fitnessFunction.clone(), combinationRepr);
    }


    @Override
    public String toString() {
        return tree.toString();
    }


    public double eval(double[] input){
        return tree.eval(input);
    }


    @Override
    public String getNumNodesAsString() {
        return getNumNodes().toString();
    }


    @Override
    public String getTrainingFitnessAsString() {
        return Utils.format(fitnessFunction.getTrainingFitness());
    }


    @Override
    public String getTestFitnessAsString() {
        return Utils.format(fitnessFunction.getTestFitness());
    }


    @Override
    public double getFitness() {
        double value = fitnessFunction.getComparableValue();
        if(Double.isInfinite(value) || Double.isNaN(value)) return Double.MAX_VALUE;
        return value;
    }


    @Override
    public double[] getTrainingSemantics() {
        return fitnessFunction.getSemantics(Utils.DatasetType.TRAINING);
    }


    @Override
    public double[] getTestSemantics() {
        return fitnessFunction.getSemantics(Utils.DatasetType.TEST);
    }


    public Map getCombinationRepr(){
        return this.combinationRepr;
    }


    /**
     * Propagate coefficients for crossover offspring.
     *
     * @param p1
     * @param p2
     * @param crossoverConst
     */
    public void propagateCrossover(GSGPIndividual p1, GSGPIndividual p2, Double crossoverConst) {
        this.addCoefficients(p1.getCombinationRepr(), crossoverConst);

        this.addCoefficients(p2.getCombinationRepr(), (1.0 - crossoverConst));
    }


    /**
     * Propagate coefficients for mutation offspring.
     *
     * @param p1
     * @param mutationT1
     * @param mutationT2
     * @param mutationStep
     */
    public void propagateMutation(GSGPIndividual p1, Node mutationT1, Node mutationT2, double mutationStep) {
        this.addCoefficients(p1.getCombinationRepr(), 1.0);

        String treeKey = mutationT1.toString();
        LinearCombinationTerm storedTerm = this.combinationRepr.get(treeKey);
        if(storedTerm != null)
            storedTerm.coefficient += mutationStep;
        else{
            combinationRepr.put(treeKey, new LinearCombinationTerm(mutationT1, mutationStep));
        }

        treeKey = mutationT2.toString();
        storedTerm = this.combinationRepr.get(treeKey);
        if(storedTerm != null)
            storedTerm.coefficient -= mutationStep;
        else{
            combinationRepr.put(treeKey, new LinearCombinationTerm(mutationT2, mutationStep * -1));

        }
    }


    /**
     * Add coefficients from a parent's representation times a constant to this individual representation.
     *
     * @param parentRepr
     * @param multiplyBy
     */
    public void addCoefficients(Map parentRepr, double multiplyBy) {
        for(Map.Entry<String, LinearCombinationTerm> entry : ((HashMap<String, LinearCombinationTerm>) parentRepr).entrySet()) {
            String treeKey = entry.getKey();
            Double coef = entry.getValue().coefficient;

            LinearCombinationTerm storedTerm = this.combinationRepr.get(treeKey);

            coef = coef * multiplyBy;

            if(storedTerm != null) {
                storedTerm.coefficient += coef;
            }
            else {
                combinationRepr.put(treeKey, entry.getValue().copy(coef));
            }
        }
    }


    /**
     * Reconstruct the individual tree to a smaller equivalent based in its coefficient representation.
     */
    public void reconstructIndividual() {
        // Root node
        Add root = new Add();

        Function current = root;

        int n = 0;
        int mapSize = combinationRepr.size();

        // Iterate over subtrees
        for(Map.Entry<String, LinearCombinationTerm> entry : combinationRepr.entrySet()) {
            n += 1;

            // Multiplication to apply coefficient to subtree
            Mul applyCoef = new Mul();

            // Include coefficient as a subnode of the multiplication
            applyCoef.addNode(new ERC(entry.getValue().coefficient), 0);

            // Get and attach subtree root node
            applyCoef.addNode(entry.getValue().tree, 1);

            // This is the last element
            if(n == mapSize) {
                // Attach new term to end the main tree
                current.addNode(applyCoef, 1);
            }
            // This is not the last element
            else {
                // Attach new term to the main tree
                current.addNode(applyCoef, 0);

                // There are at least two new terms after this one
                if(n + 1 < mapSize) {
                    // Addition operation necessary to continue the linear combination of functions
                    Add nextAdd = new Add();
                    current.addNode(nextAdd, 1);

                    current = nextAdd;
                }
            }
        }

        this.tree = root;
        this.numNodes = BigInteger.valueOf(root.getNumNodes());
    }

}