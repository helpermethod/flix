/*
 *  Copyright 2022 Nina Andrup Pedersen
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
namespace Graph {

    ///
    /// Returns the transitive closure of the directed graph `g`.
    ///
    pub def closure(g: m[(t, t)]): Set[(t, t)] with Foldable[m], Boxable[t] =
        let edges = inject g into Edge;
        let x = query edges, nodes(), reachability() select (src, dst) from Reachable(src, dst);
        List.toSet(x)

    ///
    /// Helper function.
    /// Returns a Datalog program which computes the reachable nodes when given a set of `Edge` and `Node` facts.
    ///
    def reachability(): #{ Edge(t, t), Reachable(t, t) | r } with Boxable[t] = #{
        // All nodes can reach themselves.
        Reachable(n, n) :- Node(n).
        // If `n1` can reach `m` and there is an edge from `m` to `n2`
        // then `n1` can also reach `n2`. This adds all node pairs to
        // the relational that are reachable using any number of nodes.
        Reachable(n1, n2) :- Reachable(n1, m), Edge(m, n2).
    }

    ///
    /// Returns the vertices that are reachable from the `src` in the directed graph `g`.
    ///
    pub def reachable(src: t, g: m[(t, t)]): Set[t] with Foldable[m], Boxable[t] =
        let edges = inject g into Edge;
        let x = query edges, reachabilityFromSrc(src) select dst from Reachable(dst);
        List.toSet(x)

    ///
    /// Returns the vertices that are unreachable from the `src` in the directed graph `g`.
    ///
    pub def unreachable(src: t, g: m[(t, t)]): Set[t] with Foldable[m], Boxable[t] =
        let edges = inject g into Edge;
        let unreachablility = #{
            // If there is a node that is not Reachable from src then it is unreachable.
            UnReachable(x) :- Node(x), not Reachable(x).
        };
        let x = query edges, nodes(), reachabilityFromSrc(src), unreachablility select dst from UnReachable(dst);
        List.toSet(x)

    ///
    /// Helper function for `reachable` and `unreachable`.
    /// Returns a Datalog program which computes the reachable nodes from `src` when given a set of `Edge` facts.
    ///
    def reachabilityFromSrc(src: t): #{ Edge(t, t), Reachable(t) | r } with Boxable[t] = #{
        // A node can reach itself.
        Reachable(src).
        // If `src` can reach `m` and there is an edge from `m` to `n`
        // then `src` can also reach `n`. This adds all node pairs to
        // the relational that are reachable using any number of nodes.
        Reachable(n) :- Reachable(m), Edge(m, n).
    }

    ///
    /// Returns `true` if there is a path from `src` to `dst` in the directed graph `g`.
    ///
    pub def isConnected(src: {src = t}, dst: {dst = t}, g: m[(t, t)]): Bool with Foldable[m], Boxable[t] =
        reachable(src.src, g) |> Set.exists(x -> dst.dst == x)

    ///
    /// Returns the strongly connected components of the directed graph `g`.
    ///
    pub def stronglyConnectedComponents(g: m[(t, t)]): Set[Set[t]] with Foldable[m], Boxable[t] =
        let edges = inject g into Edge;
        let connected = #{
            // If `n1` can reach `n2` and `n2` can reach `n1`
            // then they are part of the same strongly connected component.
            Connected(n1; Set#{n2}) :- Reachable(n1, n2), Reachable(n2, n1).
        };
        let components = #{
            // After the full computation of Connected, dublicates are removed
            // by checking `n` is the minimum in the strongly connected component.
            Components(s) :- fix Connected(n; s), if Some(n) == Set.minimum(s).
        };
        query edges, nodes(), reachability(), connected, components select x from Components(x) |> List.toSet

    ///
    /// Helper function.
    /// Returns a Datalog program which when given a set of `Edge` facts computes the nodes.
    ///
    def nodes(): #{ Edge(t, t), Node(t) | r } with Boxable[t] = #{
        Node(x) :- Edge(x, _).
        Node(x) :- Edge(_, x).
    }

    ///
    /// Returns `true` if the directed graph `g` contains at least one cycle.
    ///
    pub def isCyclic(g: m[(t, t)]): Bool with Foldable[m], Boxable[t] =
        let edges = inject g into Edge;
        let reachability = #{
            // Reachability given the edges.
            Reachable(n1, n2) :- Edge(n1, n2), if n1 != n2.
            // If `n1` can reach `m` and there is an edge from `m` to `n2`
            // then `n1` can also reach `n2`. This adds all node pairs to
            // the relational that are reachable using any number of nodes.
            Reachable(n1, n2) :- Reachable(n1, m), Edge(m, n2).
        };
        let selfReach = query edges, reachability select () from Reachable(x, y) where x == y;
        0 < (selfReach |> List.length)

    ///
    /// Returns the shortest distance from `src` to every other vertex in the weighted directed graph `g`.
    ///
    pub def distances(src: t, g: m[(t, Int32, t)]): Map[t, Int32] with Foldable[m], Boxable[t] =
        let edges = inject g into Edge;
        let invEdges = #{
            InvEdge(x, Down(d), y) :- Edge(x, d, y).
        };
        let dists = #{
            Dist(src; Down(0)).
            Dist(y; d + w) :- Dist(x; d), InvEdge(x, w, y).
        };

        let f = (x, acc) -> {
            let (n, Down(d)) = x;
            Map.insert(n, d, acc)
        };

        query edges, invEdges, dists select (x, d) from Dist(x; d) |> List.foldRight(f, Map.empty())

    ///
    /// Returns the shortest distance from `src` to `dst` in the weighted directed graph `g`.
    ///
    pub def distance(src: { src :: t }, dst: { dst :: t }, g: m[(t, Int32, t)]): Option[Int32] with Foldable[m], Boxable[t] =
        distances(src.src, g) |> Map.get(dst.dst)


}
