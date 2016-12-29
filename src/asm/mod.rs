
pub mod instruction;
pub mod parser;
pub mod ast;
pub mod expression;
pub mod num;

use asm::ast::{Statements,Statement,Directive};
use asm::expression::{Expression,EvaluationError};
use instruction::ToVal;
use std::collections::HashMap;

pub fn assemble(statements: &Statements) -> Result<Vec<u8>,EvaluationError> {
    let (max_pos, names) = compute_names(statements)?;
    let mut output = vec![0; max_pos];
    generate_bytes(statements, &names, &mut output)?;
    println!("names: {:?}, max_pos: {:?}", names, max_pos);
    Ok(output)
}

fn generate_bytes(statements: &Statements, names: &HashMap<String,i32>, output: &mut Vec<u8>) -> Result<(),EvaluationError> {
    let mut pos: usize = 0;
    println!("statements: {:?}", statements);
    for statement in statements.statements.iter() {
        println!("{} - {:?}", pos, statement);
        match statement {
            &Statement::Directive(ref dir) => {
                match dir {
                    &Directive::Byte(ref bytes) => {
                        for expr in bytes.iter() {
                            let b: u8 = expr.to(names)?;
                            output[pos] = b;
                            pos = pos + 1;
                        }
                    },
                    &Directive::ByteString(ref bytes) => {
                        for b in bytes.iter() {
                            output[pos] = *b;
                            pos = pos + 1;
                        }
                    },
                    &Directive::Org(ref location) => {
                        pos = *location;
                    },
                    &Directive::Word(ref words) => {
                        for expr in words.iter() {
                            let w: u16 = expr.to(names)?;;
                            output[pos] = (w & 0xFF) as u8;
                            pos = pos + 1;
                            output[pos] = ((w >> 8) & 0xFF) as u8;
                            pos = pos + 1;
                        }
                    },
                    &Directive::Include(_) => panic!("There should be no .include directives left at this point".to_string()),
                    &Directive::Cnop(ref add, ref multiple) => {
                        let add = add.eval(&names)? as usize;
                        let multiple = multiple.eval(&names)? as usize;
                        let mut mult = 0;
                        loop {
                            if pos < mult {
                                //break;
                            }
                            mult = mult + multiple;
                        }
                        pos = mult + add;
                    }
                }
            },
            &Statement::Label(ref name) => {},
            &Statement::Instruction(ref instr) => {
                let bytes = instr.reduce(names)?.to_bytes();
                for b in bytes.iter() {
                    output[pos] = *b;
                    pos = pos + 1;
                }
            },
            &Statement::Variable(ref name, ref expr) => {},
            &Statement::Alias(ref name, ref expr) => {}
        }
    }
    Ok(())
}

fn compute_names(statements: &Statements) -> Result<(usize, HashMap<String,i32>),EvaluationError> {
    let mut names = HashMap::new();
    let mut max_pos: usize = 0;
    let mut pos: i32 = 0;
    for statement in statements.statements.iter() {
        match statement {
            &Statement::Directive(ref dir) => {
                match dir {
                    &Directive::Byte(ref bytes) => pos = pos + (bytes.len() as i32),
                    &Directive::ByteString(ref bytes) => pos = pos + (bytes.len() as i32),
                    &Directive::Org(ref location) => pos = *location as i32,
                    &Directive::Word(ref words) => pos = pos + ((words.len() * 2) as i32),
                    &Directive::Include(_) => panic!("There should be no .include directives left at this point".to_string()),
                    &Directive::Cnop(ref add, ref multiple) => {
                        let add = add.eval(&names)?;
                        let multiple = multiple.eval(&names)?;
                        let mut mult = 0;
                        loop {
                            if pos < mult {
                                break;
                            }
                            mult = mult + multiple;
                        }
                        pos = mult + add;
                    }
                }
            },
            &Statement::Label(ref name) => {
                names.insert(name.to_lowercase(), pos);
            },
            &Statement::Instruction(ref instr) => {
                pos = pos + (instr.size() as i32);
            },
            &Statement::Variable(ref name, ref expr) => {
                let value = expr.eval(&names)?;
                names.insert(name.to_lowercase(), value);
            },
            &Statement::Alias(ref name, ref expr) => {
                let value = expr.eval(&names)?;
                names.insert(name.to_lowercase(), value);
            }
        }
        if max_pos < (pos as usize) {
            max_pos = pos as usize;
        }
    }
    Ok((max_pos, names))
}

