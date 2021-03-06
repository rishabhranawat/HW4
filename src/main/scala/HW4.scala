object HW4 extends js.util.JsApp {
  import js.hw4._
  import js.hw4.ast._
  
  /*
   * CSCI-UA.0480-006: Homework 4
   * <John Chen>
   * 
   * Partner: <Thomas Liu>
   * Collaborators: <Any Collaborators>
   */


  /*
   * Fill in the appropriate portions above by replacing things delimited
   * by '<'... '>'.
   * 
   * Replace the '???' expression with your code in each function.
   *
   * Do not make other modifications to this template, such as
   * - adding "extends App" or "extends Application" to your Lab object,
   * - adding a "main" method, and
   * - leaving any failing asserts.
   * 
   * Your solution will _not_ be graded if it does not compile!!
   * 
   * This template compiles without error. Before you submit comment out any
   * code that does not compile or causes a failing assert.  Simply put in a
   * '???' as needed to get something that compiles without error.
   *
   */
  
  /* Collections and Higher-Order Functions */
  
  /* Lists */
  def contains[A](item: A, list: List[A]): Boolean = {
    list match {
      case Nil => false
      case head :: tail => {
        if (item == head) true
        else contains(item, tail)
      }
    }
  }
  def reverse[A](list : List[A]) : List[A] = {
    def rev[A](result : List[A], list : List[A]) : List[A] = {
      list match {
        case Nil => result
        case (x :: xs) => { rev(x :: result, xs) }
      }
    }
    rev(Nil, list)
  }
  def addToEnd[A](item: A, list: List[A]): List[A] = {
    reverse(item :: reverse(list))
  }
  
  def compressRec[A](l: List[A]): List[A] = l match {
    case Nil | _ :: Nil => l
    case h1 :: (t1 @ (h2 :: _)) => {
      if (h1 == h2) compressRec(t1)
      else h1 :: compressRec(t1)
    }
  }
  
  def compressFold[A](l: List[A]): List[A] = l.foldRight(Nil: List[A]){
    (h, acc) => {
      acc match {
        case Nil => h :: acc
        case h1 :: t1 => {
          if (h != h1) h :: acc
          else acc
        }
      }
    }
  }
  
  
  def mapFirst[A](f: A => Option[A])(l: List[A]): List[A] = l match {
    case Nil => Nil
    case h :: t => {
      val x = f(h)
      if (x == None) h :: mapFirst(f)(t)
      else x.get :: t
    }
  }
  
  /* Search Trees */
  
  sealed abstract class Tree {
    def insert(n: Int): Tree = this match {
      case Empty => Node(Empty, n, Empty)
      case Node(l, d, r) => if (n < d) Node(l insert n, d, r) else Node(l, d, r insert n)
    } 
    
    def foldLeft[A](z: A)(f: (A, Int) => A): A = {
      def loop(acc: A, t: Tree): A = t match {
        case Empty => acc
        case Node(l, d, r) => {
          l match {
            case Empty => loop(f(acc, d), r)
            case _ => loop(acc, l)
          }
        }
      }
      loop(z, this)
    }
    
    def pretty: String = {
      def p(acc: String, t: Tree, indent: Int): String = t match {
        case Empty => acc
        case Node(l, d, r) =>
          val spacer = " " * indent
          p("%s%d%n".format(spacer, d) + p(acc, l, indent + 2), r, indent + 2)
      } 
      p("", this, 0)
    }
  }
  case object Empty extends Tree
  case class Node(l: Tree, d: Int, r: Tree) extends Tree
  
  def treeFromList(l: List[Int]): Tree =
    l.foldLeft(Empty: Tree){ (acc, i) => acc insert i }
  
  def sum(t: Tree): Int = t.foldLeft(0){ (acc, d) => acc + d }
  
  def strictlyOrdered(t: Tree): Boolean = {
    val (b, _) = t.foldLeft((true, None: Option[Int])){
      def rec(h: (Boolean, Option[Int]), acc: Int): (Boolean, Option[Int]) = {
        if (h._1 == false) (false, None) 
        else if (h._2 == None) (true, Option(acc))
        else if (h._2.get < acc) (true, Option(acc))
        else (false, None)
      }
      (h, acc) => rec(h, acc)
    }
    b
  }
  

  /* Type Inference */
  
  // A helper function to check whether a JS type has a function type in it.
  // While this is completely given, this function is worth studying to see
  // how library functions are used.
  def hasFunctionTyp(t: Typ): Boolean = t match {
    case TFunction(_, _) => true
    case TObj(fs) => fs exists { case (_, t) => hasFunctionTyp(t) }
    case _ => false
  }
  
  def typeInfer(env: Map[String, Typ], e: Expr): Typ = {
    // Some shortcuts for convenience
    def typ(e1: Expr) = typeInfer(env, e1)
    def err[T](tgot: Typ, e1: Expr): T = throw StaticTypeError(tgot, e1)

    e match {
      case Print(e1) => typ(e1); TUndefined
      case Num(_) => TNumber
      case Bool(_) => TBool
      case Undefined => TUndefined
      case Str(_) => TString
      case Var(x) => env(x)
      case ConstDecl(x, e1, e2) => typeInfer(env + (x -> typ(e1)), e2)
      case UnOp(UMinus, e1) => typ(e1) match {
        case TNumber => TNumber
        case tgot => err(tgot, e1)
      }
      case UnOp(Not, e1) => typ(e1)
      case BinOp(Plus, e1, e2) => typ(e1) == typ(e2) match {
        case true => typ(e1) match {
          case TNumber => TNumber
          case TString => TString
          case _ => err(typ(e1), e1)
        }
        case false => err(typ(e1), e1)
      }
      case BinOp(Minus|Times|Div, e1, e2) => TNumber
      case BinOp(Eq|Ne, e1, e2) => TBool
      case BinOp(Lt|Le|Gt|Ge, e1, e2) => TBool
      case BinOp(And|Or, e1, e2) => TBool
      case BinOp(Seq, e1, e2) => typ(e2)
      case If(e1, e2, e3) => typ(e2)
      case Function(p, xs, tann, e1) => {
        // Bind to env1 an environment that extends env with an appropriate binding if
        // the function is potentially recursive.
        val env1 = (p, tann) match {
          case (Some(f), Some(tret)) =>
            val tprime = TFunction(xs, tret)
            env + (f -> tprime)
          case (None, _) => env
          case _ => err(TUndefined, e1)
        }
        // Bind to env2 an environment that extends env1 with bindings for xs.
        val env2 = {
          xs.foldLeft(env1){
            case (env2, (s,t)) => env2 + (s -> t)
          }
        }
        // Match on whether the return type is specified.
        tann match {
          case None => TFunction(xs, typeInfer(env2, e1))
          case Some(tret) => {
            val bodyType = typeInfer(env2, e1)
            if (bodyType == tret) TFunction(xs,tret)
            else err(bodyType,e1)
          }
        }
      }
      case Call(e1, es) => 
        //println(e.prettyJS() + " type of e1:" + print.prettyTyp(typ(e1)))
        typ(e1) match {
        case TFunction(txs, tret) if (txs.length == es.length) => {
          (txs, es).zipped.foreach {
            case ((x,t), e) => if (t != typ(e)) err(t, e)
          }
          tret
        }
        case tgot => err(tgot, e1)
      }
      case Obj(fs) => {
        TObj(fs.mapValues { typ(_) })
      }
      case GetField(e1, f) => 
        typ(e1) match {
        case TObj(e) => e.get(f).get
        case _ => err(typ(e1), e1)
      }
    }
  }
  
  
  /* Small-Step Interpreter */
  
  def inequalityVal(bop: Bop, v1: Expr, v2: Expr): Boolean = {
    require(bop == Lt || bop == Le || bop == Gt || bop == Ge)
    ((v1, v2): @unchecked) match {
      case (Str(s1), Str(s2)) =>
        (bop: @unchecked) match {
          case Lt => s1 < s2
          case Le => s1 <= s2
          case Gt => s1 > s2
          case Ge => s1 >= s2
        }
      case (Num(n1), Num(n2)) =>
        (bop: @unchecked) match {
          case Lt => n1 < n2
          case Le => n1 <= n2
          case Gt => n1 > n2
          case Ge => n1 >= n2
        }
    }
  }
  
  def substitute(e: Expr, x: String, v: Expr): Expr = {
    require(isValue(v) && closed(v))
    
    def subst(e: Expr): Expr = substitute(e, x, v)
    
    e match {
      case Num(_) | Bool(_) | Undefined | Str(_) => e
      case Print(e1) => Print(subst(e1))
      case UnOp(uop, e1) => UnOp(uop, subst(e1))
      case BinOp(bop, e1, e2) => BinOp(bop, subst(e1), subst(e2))
      case If(e1, e2, e3) => If(subst(e1), subst(e2), subst(e3))
      case Var(y) => if (x == y) v else e //if match new else old
      case ConstDecl(y, e1, e2) => ConstDecl(y, subst(e1), if (x == y) e2 else subst(e2))
      case Function(p, xs, tann, e1) => {
        Function(p, xs, tann, subst(e1))
      }
      case Call(e1, es) => {
        val listNew = es.foldLeft(es){
          (es, e1) => es.::(subst(e1)) 
        }
        Call(subst(e1), listNew)
      }
        //Call(subst(e1), es) // look for all x's in e1 which is a function and es which are parameters, 
        // substitute in to replace x with the value and then done. no need to evaluate
      // if any of the x's match any of the parameters, then that x is not free, so just leave this case return the whole thing unchanged
      // xs.exists(_.1 == x)
      // or
      // (xs._1 ++ p).exists(_ == x)
      case Obj(fs) => {
        val newMap: Map[String, Expr] = Map() 
        val newList = {
          fs.foldLeft(newMap){
            case (m, (s, e)) => m.+((s,e))
          }
        }
        Obj(newList)
      }
      // look into object fields and subtitute the values {f1 : x, f2 : 3}  [4/x] = {f1: 4, f2: 3}
      case GetField(e1, f) => e1 match {
        case (Obj(ts)) => ts.get(f).get
      }
    }
  }
  
  def step(e: Expr): Expr = {
    require(!isValue(e))
    assume(closed(e))
    def stepIfNotValue(e: Expr): Option[Expr] = if (isValue(e)) None else Some(step(e))
    
    val e1 = e match {
      /* Base Cases: Do Rules */
      case Print(v1) if isValue(v1) => println(v1.prettyVal); Undefined
      //do uminus
      case UnOp(UMinus, Num(n1)) => Num(- n1)
      //do not
      case UnOp(Not, Bool(b1)) => Bool(! b1)
      //do seq
      case BinOp(Seq, v1, e2) if isValue(v1) => e2
      //do plus str
      case BinOp(Plus, Str(s1), Str(s2)) => Str(s1 + s2)
      //do arith
      case BinOp(Plus, Num(n1), Num(n2)) => Num(n1 + n2)
      case BinOp(Times, Num(n1), Num(n2)) => Num(n1 * n2)
      case BinOp(Minus, Num(n1), Num(n2)) => Num(n1 - n2)
      case BinOp(Div, Num(n1), Num(n2)) => Num(n1 / n2)
      //do ineq num and str
      case BinOp(bop @ (Lt|Le|Gt|Ge), v1, v2) if isValue(v1) && isValue(v2) => 
        Bool(inequalityVal(bop, v1, v2))
      //do eq
      case BinOp(Eq, v1, v2) if isValue(v1) && isValue(v2) => Bool(v1 == v2)
      //do ne
      case BinOp(Ne, v1, v2) if isValue(v1) && isValue(v2) => Bool(v1 != v2)
      //do and
      case BinOp(And, Bool(b1), e2) => if (b1) e2 else Bool(false)
      //do or true
      case BinOp(Or, Bool(b1), e2) => if (b1) Bool(true) else e2
      //do if then
      case If(Bool(true), e2, e3) => e2
      //do if else
      case If(Bool(false), e2, e3) => e3
      //do const
      case ConstDecl(x, v1, e2) if isValue(v1) => substitute(e2, x, v1)
      //do call and rec
      case Call(v1, es) if isValue(v1) && (es forall isValue) =>
        v1 match {
          case Function(p, txs, _, e1) => {
            val e1p = (txs, es).zipped.foldRight(e1){
              case ((x,t), eNew) => substitute(e, x._1, eNew)
            }
            p match {
              case None => e1p
              case Some(x1) => substitute(e, x1, v1)
            }
          }
          case _ => throw new StuckError(e)
        }
      /*** Fill-in more cases here. ***/
        
      /* Inductive Cases: Search Rules */
      //search print
      case Print(e1) => Print(step(e1))
      //search uop
      case UnOp(uop, e1) => UnOp(uop, step(e1))
      //search bop2
      case BinOp(bop, v1, e2) if isValue(v1) => BinOp(bop, v1, step(e2))
      //Search bop1
      case BinOp(bop, e1, e2) => BinOp(bop, step(e1), e2)
      //search if
      case If(e1, e2, e3) => If(step(e1), e2, e3)
      //search const
      case ConstDecl(x, e1, e2) => ConstDecl(x, step(e1), e2)
      /*** Fill-in more cases here. ***/
      // Search obj
      case Obj(fs) => {
        Obj(fs.mapValues { x => step(x) })
      }
      //search getfield
      case GetField(e1, f) if(!isValue(e1)) => GetField(step(e1), f)
      //search call2
      case Call(e1, es) if isValue(e1) =>
        val l:List[Expr] = List()
        val newES = es.foldLeft(l){
          case (l1, e1) => l1.+:(step(e1))
        }
        Call(e1, newES)
      //search call1
      case Call(e1, es) => Call(step(e1), es)
      /* Everything else is a stuck error. Should not happen if e is well-typed. */
      case _ => throw StuckError(e)
    }
    if (e1.pos == null) e1.pos = e.pos
    e1
  } ensuring (e1 => closed(e1))
  
  
  /* External Interfaces */
  
  override def init(): Unit = {
    this.debug = true // comment this out or set to false if you don't want to print debugging information
  }
  
  def inferType(e: Expr): Typ = {
    if (debug) {
      println("------------------------------------------------------------")
      println("Type checking: %s ...".format(e))
    } 
    val t = typeInfer(Map.empty, e)
    if (debug) {
      println("Type: " + t.pretty)
    }
    t
  }
  
  // Interface to run your small-step interpreter and print out the steps of evaluation if debugging. 
  def iterateStep(e: Expr): Expr = {
    require(closed(e))
    def loop(e: Expr, n: Int): Expr = {
      if (debug) { println("Step %s: %s".format(n, e)) }
      if (isValue(e)) e else loop(step(e), n + 1)
    }
    if (debug) {
      println("------------------------------------------------------------")
      println("Evaluating with step ...")
    }
    val v = loop(e, 0)
    if (debug) {
      println("Value: " + v)
    }
    v
  }

  // Convenience to pass in a js expression as a string.
  def iterateStep(s: String): Expr = iterateStep(parse.fromString(s))
  
  // Interface for main
  def processFile(file: java.io.File) {
    if (debug) {
      println("============================================================")
      println("File: " + file.getName)
      println("Parsing ...")
    }
    
    val expr =
      handle(fail()) {
        parse.fromFile(file)
      }
    
    if (debug) {
      println("Parsed expression:")
      println(expr.prettyJS())
    }
    
    handle(fail()) {
      val t = inferType(expr)
    }
    
    handle() {
      val v1 = iterateStep(expr)
      println(v1.prettyVal)
    }
  }

}