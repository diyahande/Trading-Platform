import { ApiProperty } from '@nestjs/swagger';
import { IsDateString, IsEmail, IsString, Length, Matches } from 'class-validator';

export class CreateSignupRequestDto {
  @ApiProperty({ minLength: 1, maxLength: 64 })
  @IsString()
  @Length(1, 64)
  firstName!: string;

  @ApiProperty({ minLength: 1, maxLength: 64 })
  @IsString()
  @Length(1, 64)
  lastName!: string;

  @ApiProperty({ format: 'email', maxLength: 254 })
  @IsEmail()
  @Length(5, 254)
  username!: string;

  @ApiProperty({ minLength: 8, maxLength: 20 })
  @IsString()
  @Length(8, 20)
  @Matches(/^[0-9+()\-\s]+$/)
  phone!: string;

  @ApiProperty({ format: 'date' })
  @IsDateString()
  dob!: string;
}
