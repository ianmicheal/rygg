
module Location: sig
  type t

  val source : t -> string
  val line : t -> int
  val column : t -> int
  val offset : t -> int

  val to_string : t -> string
  val empty : t

  val update : t -> string -> int -> t
  val create : string -> int -> int -> int -> t
  val inc_line : t -> t
  val inc_column : t -> t
  val with_source : t -> string -> t

  val merge : t -> t -> t
end

type t = { start_pos: Location.t; end_pos: Location.t }
val make : Location.t -> Location.t -> t
val merge : t -> t -> t
val to_string : t -> string
val to_colored_string : t -> string
